# Rate Limiter as a Service

A production-grade API Gateway with traffic control — built with Java 21, Spring Boot, Redis, and PostgreSQL.

> Standalone rate limiting service that any API can plug into to control request rates per client, plan, endpoint, and region.

<img width="2048" height="1037" alt="Design sem nome(2)" src="https://github.com/user-attachments/assets/b5a94f5e-b827-4c93-a1c8-037fc4f42f98" />

---

## Why this project stands out

Most rate limiter tutorials implement a simple counter. This project goes further:

- **Three algorithms** with documented trade-offs — Fixed Window, Token Bucket, and Sliding Window Log
- **Atomic operations** via Lua Scripts on Redis — exactly how Stripe and Cloudflare do it in production
- **Multi-tenant** with per-plan limits — free, pro, and enterprise clients with different quotas
- **Circuit Breaker** with Resilience4j — the API survives even when Redis goes down
- **Real observability** — Prometheus metrics + Grafana dashboard showing blocked requests/second, p95 latency, and top consumers
- **One-command setup** — everything runs with `docker-compose up -d`

---

## Architecture

```
[Client] → API Gateway (this service) → [Upstream API]
                  ↓
            Redis Cluster
            (counters + Lua scripts)
                  ↓
            PostgreSQL
            (plans + clients + audit log)
                  ↓
            Prometheus + Grafana
            (real-time observability)
```

### Layer structure (Clean Architecture)

```
src/main/java/com/ratelimiter/
├── domain/              # Pure business logic — no frameworks, no Redis, no Spring
│   ├── model/           # Client, Plan, RateLimitResult, WindowType
│   ├── repository/      # ClientRepository (interface)
│   └── service/         # RateLimiter (interface)
│
├── application/         # Use cases — orchestrates domain, knows nothing about infrastructure
│   └── usecase/         # CheckRateLimitUseCase, RegisterClientUseCase, CreatePlanUseCase
│
├── infrastructure/      # Technical implementations — Redis, PostgreSQL, Flyway
│   ├── redis/           # RedisRateLimiter, TokenBucketRateLimiter, SlidingWindowRateLimiter
│   ├── persistence/     # ClientJpaRepository, ClientRepositoryAdapter, PlanEntity, ClientEntity
│   └── config/          # RedisConfig, CircuitBreakerConfig
│
└── interfaces/          # HTTP layer — Spring MVC only
    ├── filter/          # RateLimitFilter (intercepts every request)
    ├── controller/      # AdminController
    └── exception/       # GlobalExceptionHandler
```

The golden rule: **inner layers never depend on outer layers.** The domain has zero knowledge of Redis, Spring, or PostgreSQL. If you want to swap Redis for Memcached tomorrow, only `infrastructure/redis` changes.

---

## Rate limiting algorithms

### 1. Fixed Window Counter

The simplest algorithm. Counts requests in a fixed time window (e.g., 0:00–1:00, 1:00–2:00).

**How it works:**
```
Window: 0s → 60s
Requests: 1, 2, 3 ... 100 ✅
Request 101 ❌ — blocked until window resets at 60s
```

**Trade-off:** A client can send 100 requests at 0:59 and 100 more at 1:01 — effectively 200 requests in 2 seconds. Acceptable for high-throughput scenarios where small bursts are tolerable.

**Implementation:** Lua script with `INCR` + `EXPIRE` — atomic, no race conditions.

```lua
local count = redis.call('INCR', KEYS[1])
if count == 1 then
    redis.call('EXPIRE', KEYS[1], ARGV[2])
end
```

---

### 2. Token Bucket

A bucket holds tokens. Each request consumes one token. Tokens refill gradually over time.

**How it works:**
```
Bucket capacity: 100 tokens
Refill rate: ~1.6 tokens/second

Client idle for 10s → accumulates 16 tokens → can burst 16 requests
Client exhausts bucket → blocked until tokens refill
```

**Trade-off:** Fair to clients who pause and resume. Allows legitimate bursts. More complex than Fixed Window — stores two values per client (token count + last refill timestamp).

**Implementation:** Lua script calculating elapsed time and refilling tokens atomically.

---

### 3. Sliding Window Log *(default)*

Stores the exact timestamp of every request in a Redis Sorted Set. On each new request, removes timestamps older than the window and counts what remains.

**How it works:**
```
Sorted Set: [14:00:00.100, 14:00:00.300, 14:00:00.800, ...]
New request at 14:01:05:
  → remove everything before 14:00:05
  → count remaining
  → if count < limit: allow + add timestamp
  → if count >= limit: block
```

**Trade-off:** Maximum precision — impossible to burst at window boundaries. Higher memory usage (one entry per request). Best for financial APIs, payment systems, or any context where "exactly 100 per minute" cannot have margin.

**Implementation:** `ZADD` + `ZREMRANGEBYSCORE` + `ZCARD` in a single Lua script.

---

## Switching algorithms

Changing the algorithm is a one-line change — just move the `@Primary` annotation:

```java
// infrastructure/redis/SlidingWindowRateLimiter.java
@Primary  // ← active algorithm
@Component("slidingWindowRateLimiter")
public class SlidingWindowRateLimiter implements RateLimiter { ... }

// infrastructure/redis/TokenBucketRateLimiter.java
// @Primary  ← remove to deactivate
@Component("tokenBucketRateLimiter")
public class TokenBucketRateLimiter implements RateLimiter { ... }
```

This is possible because all three implement the same `RateLimiter` interface from the domain layer.

---

## Circuit Breaker

If Redis becomes unavailable, the Circuit Breaker activates and applies a configurable fallback policy.

```
Redis healthy    → CLOSED   → normal rate limiting
Redis failing    → OPEN     → fallback policy applied
Redis recovering → HALF-OPEN → tests 3 requests before closing
```

### Fail Open vs Fail Closed

Configure in `application.yml`:

```yaml
rate-limiter:
  fallback-policy: FAIL_OPEN   # or FAIL_CLOSED
```

| Policy | Behavior | Use when |
|---|---|---|
| `FAIL_OPEN` | All requests pass through | Availability > precision (streaming, social, e-commerce) |
| `FAIL_CLOSED` | All requests blocked (503) | Security > availability (payments, banking, healthcare) |

This is a **business decision**, not a technical one. A 30-second outage on a payment API that lets 10,000 uncontrolled requests through costs more than being down for 30 seconds.

---

## Standard response headers

Every response includes rate limit headers — whether 200 OK or 429 Too Many Requests:

```
X-RateLimit-Limit:     100
X-RateLimit-Remaining: 43
X-RateLimit-Reset:     1710000000
Retry-After:           30        (only on 429)
```

This is the same standard used by Stripe, GitHub, and Cloudflare.

---

## Multi-tenant plans

Each client belongs to a plan with distinct limits per time window:

| Plan | req/min | req/hour | req/day |
|---|---|---|---|
| free | 100 | 1,000 | 10,000 |
| pro | 1,000 | 20,000 | 200,000 |
| enterprise | 10,000 | 500,000 | 5,000,000 |

Plans are stored in PostgreSQL and cached in Redis. Changing a client's plan takes effect immediately.

---

## Tech stack

| Layer | Technology |
|---|---|
| Core | Java 21 + Spring Boot 4 |
| Cache / Counters | Redis + Lua Scripts |
| Persistence | PostgreSQL + Flyway |
| Resilience | Resilience4j Circuit Breaker |
| Observability | Micrometer + Prometheus + Grafana |
| Tests | JUnit 5 + Testcontainers |
| Infrastructure | Docker Compose |

---

## Getting started

### Prerequisites

- Docker Desktop
- Java 21
- Maven 3.9+

### Run everything with one command

```bash
# 1. Build the application
cd api-gateway
mvn clean package -DskipTests
cd ..

# 2. Start all services
docker-compose up -d
```

This starts:
- **API Gateway** on `http://localhost:8080`
- **PostgreSQL** on `localhost:5433`
- **Redis** on `localhost:6379`
- **Prometheus** on `http://localhost:9090`
- **Grafana** on `http://localhost:3000` (admin/admin)

### Create a test client

```bash
docker exec -it rate-limiter-postgres-1 psql -U postgres -d ratelimiter -c \
  "INSERT INTO clients (api_key, name, plan_id, active)
   SELECT 'my-api-key', 'Test Client', id, true
   FROM plans WHERE name = 'free';"
```

### Make a request

```bash
curl -H "X-API-Key: my-api-key" http://localhost:8080/api/products
```

Response headers:
```
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 99
X-RateLimit-Reset: 1774709457
```

---

## Observability

### Prometheus metrics

Available at `http://localhost:9090`. Key metric:

```promql
# Requests per second by status
rate(ratelimit_requests_total[1m])

# Only blocked requests
rate(ratelimit_requests_total{status="blocked"}[1m])

# Top 5 clients by consumption
topk(5, sum by (client) (rate(ratelimit_requests_total[5m])))

# Block rate percentage
sum(rate(ratelimit_requests_total{status="blocked"}[1m]))
/
sum(rate(ratelimit_requests_total[1m])) * 100
```

### Grafana dashboard

Open `http://localhost:3000` (admin/admin) to see:
- Request rate by status (allowed/blocked/unauthorized)
- Top clients by consumption
- Circuit Breaker state

---

## Database schema

```sql
CREATE TABLE plans (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name                VARCHAR(50)  NOT NULL UNIQUE,
    requests_per_minute INT          NOT NULL,
    requests_per_hour   INT          NOT NULL,
    requests_per_day    INT          NOT NULL,
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE clients (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    api_key    VARCHAR(64)  NOT NULL UNIQUE,
    name       VARCHAR(100) NOT NULL,
    plan_id    UUID         NOT NULL REFERENCES plans(id),
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_clients_api_key ON clients(api_key);
```

Schema is managed by Flyway — migrations run automatically on startup, no manual SQL scripts needed.

---

## Why Lua Scripts?

Without atomic operations, two servers reading the counter simultaneously would both see `99`, both allow the request, and the client effectively bypasses the limit.

```
Server A: GET counter → 99
Server B: GET counter → 99  ← race condition
Server A: INCR → 100 ✅
Server B: INCR → 101 ✅ ← should have been blocked
```

Lua Scripts run inside Redis as a single atomic operation — no other command can execute between the read and the write. This is how Stripe, Cloudflare, and other high-scale systems implement rate limiting in production.

---

## Architectural decisions

**Why Clean Architecture?**
If Redis is replaced tomorrow, only `infrastructure/redis` changes. The domain, application layer, and HTTP layer are untouched. This was proven during development — switching between three rate limiting algorithms required zero changes outside the infrastructure layer.

**Why three algorithms?**
Different use cases demand different guarantees. A social media API tolerates Fixed Window bursts. A financial API needs Sliding Window precision. Token Bucket sits in between. Supporting all three with a one-line switch demonstrates understanding of production trade-offs, not just implementation.

**Why Fail Open vs Fail Closed?**
Because the right answer depends on business context, not technical preference. Encoding both options and making them configurable forces an explicit decision — the kind of decision senior engineers are expected to own.

---

## Project roadmap

- [x] Fixed Window Counter with Lua Scripts
- [x] Token Bucket algorithm
- [x] Sliding Window Log algorithm
- [x] Multi-tenant with plans (free/pro/enterprise)
- [x] Circuit Breaker with configurable fallback
- [x] Standard rate limit response headers
- [x] Prometheus metrics + Grafana dashboard
- [x] One-command Docker Compose setup
- [ ] Load testing with K6
- [ ] Admin REST API for client management
- [ ] Redis Cluster support
- [ ] Per-endpoint limit overrides

---

## License

MIT

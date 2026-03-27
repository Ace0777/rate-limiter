package com.ratelimiter.api_gateway.infrastructure.redis;

import com.ratelimiter.api_gateway.domain.model.Client;
import com.ratelimiter.api_gateway.domain.model.Plan;
import com.ratelimiter.api_gateway.domain.model.RateLimitResult;
import com.ratelimiter.api_gateway.domain.model.WindowType;
import com.ratelimiter.api_gateway.domain.service.RateLimiter;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
public class RedisRateLimiter implements RateLimiter {

    private final StringRedisTemplate redisTemplate;
    private final RedisScript<List<Long>> fixedWindowScript;

    @Override
    public RateLimitResult check(Client client, String endpoint) {
        Plan plan = client.getPlan();
        WindowType window = WindowType.MINUTE;

        String key    = client.rateLimitKey(endpoint, window.name());
        int    limit  = plan.getRequestsPerMinute();
        int    ttl    = window.toSeconds();

        List<Long> result = redisTemplate.execute(
                fixedWindowScript,
                List.of(key),           // KEYS
                String.valueOf(limit),  // ARGV[1]
                String.valueOf(ttl)     // ARGV[2]
        );

        boolean allowed    = result.get(0) == 1L;
        int     current    = result.get(1).intValue();
        int     retryAfter = result.get(2).intValue();
        long    resetAt    = Instant.now().getEpochSecond() + retryAfter;

        if (allowed) {
            return RateLimitResult.allowed(limit, limit - current, resetAt);
        }
        return RateLimitResult.denied(limit, resetAt, retryAfter);
    }
}

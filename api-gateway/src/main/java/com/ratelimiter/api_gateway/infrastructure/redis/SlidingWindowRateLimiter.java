package com.ratelimiter.api_gateway.infrastructure.redis;

import com.ratelimiter.api_gateway.domain.model.Client;
import com.ratelimiter.api_gateway.domain.model.Plan;
import com.ratelimiter.api_gateway.domain.model.RateLimitResult;
import com.ratelimiter.api_gateway.domain.service.RateLimiter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component("slidingWindowRateLimiter")
@Primary
@RequiredArgsConstructor
public class SlidingWindowRateLimiter implements RateLimiter {

    private final StringRedisTemplate redisTemplate;
    private final RedisScript<List<Long>> slidingWindowScript;

    private static final long WINDOW_MS = 60_000L; // 1 minuto em ms

    @Override
    public RateLimitResult check(Client client, String endpoint) {

        Plan plan  = client.getPlan();
        int limit  = plan.getRequestsPerMinute();
        long nowMs = Instant.now().toEpochMilli();

        String key = "sw:" + client.getApiKey() + ":" + endpoint;

        List<Long> result = redisTemplate.execute(
                slidingWindowScript,
                List.of(key),
                String.valueOf(limit),
                String.valueOf(nowMs),
                String.valueOf(WINDOW_MS)
        );

        boolean allowed   = result.get(0) == 1L;
        int current       = result.get(1).intValue();
        int retryAfter    = result.get(2).intValue();
        long resetAt      = Instant.now().getEpochSecond() + 60;

        if (allowed) {
            return RateLimitResult.allowed(limit, limit - current, resetAt);
        }
        return RateLimitResult.denied(limit, resetAt, retryAfter);

    }
}

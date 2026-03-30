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

@Component("tokenBucketRateLimiter")
@Primary
@RequiredArgsConstructor
public class TokenBucketRateLimiter implements RateLimiter {

    private final StringRedisTemplate redisTemplate;
    private final RedisScript<List<Long>> tokenBucketScript;

    private static final double REFILL_RATE_DIVISOR = 60.0;

    @Override
    public RateLimitResult check(Client client, String endpoint) {
        Plan plan = client.getPlan();

        int capacity   = plan.getRequestsPerMinute();
        int refillRate = (int) Math.ceil(capacity / REFILL_RATE_DIVISOR);
        long nowMs     = Instant.now().toEpochMilli();

        String key = "tb:" + client.getApiKey() + ":" + endpoint;

        List<Long> result = redisTemplate.execute(
                tokenBucketScript,
                List.of(key),
                String.valueOf(capacity),
                String.valueOf(refillRate),
                String.valueOf(nowMs)
        );

        boolean allowed   = result.get(0) == 1L;
        int remaining     = result.get(1).intValue();
        int extra         = result.get(2).intValue();
        long resetAt      = Instant.now().getEpochSecond() + 60;

        if (allowed) {
            return RateLimitResult.allowed(capacity, remaining, resetAt);
        }
        return RateLimitResult.denied(capacity, resetAt, extra);
    }
}

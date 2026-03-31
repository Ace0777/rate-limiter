package com.ratelimiter.api_gateway.infrastructure.redis;

import com.ratelimiter.api_gateway.domain.model.Client;
import com.ratelimiter.api_gateway.domain.model.RateLimitResult;
import com.ratelimiter.api_gateway.domain.service.RateLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Slf4j
@Component
public class FallbackRateLimiter implements RateLimiter {

    @Value("${rate-limiter.fallback-policy:FAIL_OPEN}")
    private String fallbackPolicy;

    @Override
    public RateLimitResult check(Client client, String endpoint) {
        log.warn("Redis unavailable — applying fallback policy: {}", fallbackPolicy);

        if ("FAIL_CLOSED".equals(fallbackPolicy)) {
            return RateLimitResult.denied(0, Instant.now().getEpochSecond() + 30, 30);
        }

        return RateLimitResult.allowed(
                client.getPlan().getRequestsPerMinute(),
                client.getPlan().getRequestsPerMinute(),
                Instant.now().getEpochSecond() + 60
        );
    }
}

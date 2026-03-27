package com.ratelimiter.api_gateway.domain.model;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RateLimitResult {

    private final boolean allowed;
    private final int limit;
    private final int remaining;
    private final long resetEpochSeconds;
    private final int retryAfterSeconds;

    public static RateLimitResult allowed(int limit, int remaining, long resetAt) {
        return RateLimitResult.builder()
                .allowed(true)
                .limit(limit)
                .remaining(remaining)
                .resetEpochSeconds(resetAt)
                .build();
    }

    public static RateLimitResult denied(int limit, long resetAt, int retryAfter) {
        return RateLimitResult.builder()
                .allowed(false)
                .limit(limit)
                .remaining(0)
                .resetEpochSeconds(resetAt)
                .retryAfterSeconds(retryAfter)
                .build();
    }
}

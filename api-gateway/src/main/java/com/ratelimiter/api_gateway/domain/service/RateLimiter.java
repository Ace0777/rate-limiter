package com.ratelimiter.api_gateway.domain.service;

import com.ratelimiter.api_gateway.domain.model.Client;
import com.ratelimiter.api_gateway.domain.model.RateLimitResult;

public interface RateLimiter {
    RateLimitResult check(Client client, String endpoint);
}

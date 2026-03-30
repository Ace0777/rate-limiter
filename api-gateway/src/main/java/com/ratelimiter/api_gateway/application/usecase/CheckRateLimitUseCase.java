package com.ratelimiter.api_gateway.application.usecase;

import com.ratelimiter.api_gateway.domain.model.Client;
import com.ratelimiter.api_gateway.domain.model.RateLimitResult;
import com.ratelimiter.api_gateway.domain.repository.ClientRepository;
import com.ratelimiter.api_gateway.domain.service.RateLimiter;
import com.ratelimiter.api_gateway.interfaces.exception.ClientInactiveException;
import com.ratelimiter.api_gateway.interfaces.exception.ClientNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CheckRateLimitUseCase {

    private final ClientRepository clientRepository;

    @Qualifier("tokenBucketRateLimiter")
    private final RateLimiter rateLimiter;

    public RateLimitResult execute(String apiKey, String endpoint) {
        Client client = clientRepository
                .findByApiKey(apiKey)
                .orElseThrow(() -> new ClientNotFoundException(apiKey));

        if (!client.isAllowedToCall()) {
            throw new ClientInactiveException(apiKey);
        }

        return rateLimiter.check(client, endpoint);
    }
}

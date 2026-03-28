package com.ratelimiter.api_gateway.domain.repository;

import com.ratelimiter.api_gateway.domain.model.Client;

import java.util.Optional;

public interface ClientRepository {
    Optional<Client> findByApiKey(String apiKey);
    Client save(Client client);
}

package com.ratelimiter.api_gateway.infrastructure.percistance;

import com.ratelimiter.api_gateway.domain.model.Client;
import com.ratelimiter.api_gateway.domain.model.Plan;
import com.ratelimiter.api_gateway.domain.repository.ClientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ClientRepositoryAdapter implements ClientRepository {

    private final ClientJpaRepository jpa;

    @Override
    public Optional<Client> findByApiKey(String apiKey) {
        return jpa.findByApiKey(apiKey)
                .map(this::toDomain);
    }

    @Override
    public Client save(Client client) {
        ClientEntity saved = jpa.save(toEntity(client));
        return toDomain(saved);
    }


    private Client toDomain(ClientEntity e) {
        Plan plan = Plan.builder()
                .id(e.getPlan().getId().toString())
                .name(e.getPlan().getName())
                .requestsPerMinute(e.getPlan().getRequestsPerMinute())
                .requestsPerHour(e.getPlan().getRequestsPerHour())
                .requestsPerDay(e.getPlan().getRequestsPerDay())
                .build();

        return Client.builder()
                .id(e.getId().toString())
                .apiKey(e.getApiKey())
                .name(e.getName())
                .plan(plan)
                .active(e.isActive())
                .build();
    }

    private ClientEntity toEntity(Client c) {
        return ClientEntity.builder()
                .apiKey(c.getApiKey())
                .name(c.getName())
                .active(c.isActive())
                .build();
    }
}

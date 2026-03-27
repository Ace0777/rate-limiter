package com.ratelimiter.api_gateway.infrastructure.percistance;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "plans")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlanEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(nullable = false)
    private int requestsPerMinute;

    @Column(nullable = false)
    private int requestsPerHour;

    @Column(nullable = false)
    private int requestsPerDay;
}

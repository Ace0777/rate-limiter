package com.ratelimiter.api_gateway.domain.model;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class Plan {

    private final String id;
    private final String name;
    private final int requestsPerMinute;
    private final int requestsPerHour;
    private final int requestsPerDay;

    public boolean allows(int currentCount, WindowType window){
        return switch (window){
            case MINUTE -> currentCount < requestsPerMinute;
            case HOUR   -> currentCount < requestsPerHour;
            case DAY    -> currentCount < requestsPerDay;
        };
    }

}

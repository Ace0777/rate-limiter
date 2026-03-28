package com.ratelimiter.api_gateway.domain.model;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class Client {

    private final String id;
    private final String apiKey;
    private String name;
    private final Plan plan;
    private final boolean active;

    public boolean isAllowedToCall(){
        return active;
    }

    //redis
    public String rateLimitKey(String endpoint, String window) {
        return String.format("rl:%s:%s:%s", apiKey, endpoint, window);
    }
}

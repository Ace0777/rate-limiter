package com.ratelimiter.api_gateway.domain.model;

public enum WindowType {
    MINUTE,
    HOUR,
    DAY;

    public int toSeconds() {
        return switch (this) {
            case MINUTE -> 60;
            case HOUR   -> 3600;
            case DAY    -> 86400;
        };
    }
}

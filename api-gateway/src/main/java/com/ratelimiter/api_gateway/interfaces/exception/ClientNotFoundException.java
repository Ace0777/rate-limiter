package com.ratelimiter.api_gateway.interfaces.exception;

public class ClientNotFoundException extends RuntimeException {
    public ClientNotFoundException(String apiKey) {
        super("Client not found for API key: " + apiKey);
    }
}


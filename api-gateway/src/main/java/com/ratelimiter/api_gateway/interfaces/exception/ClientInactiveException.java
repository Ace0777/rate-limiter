package com.ratelimiter.api_gateway.interfaces.exception;


public class ClientInactiveException extends RuntimeException {
    public ClientInactiveException(String apiKey) {
        super("Client is inactive: " + apiKey);
    }
}

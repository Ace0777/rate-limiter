package com.ratelimiter.api_gateway.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

@Configuration
public class RedisConfig {
    @Bean
    public RedisScript<List<Long>> fixedWindowScript() {
        return RedisScript.of(
                new ClassPathResource("scripts/sliding_window.lua"),
                (Class<List<Long>>) (Class<?>) List.class
        );
    }
}

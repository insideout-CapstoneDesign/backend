package com.insideout.backend.global.infra.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ai.service")
public record AiProperties (
        String baseUrl,
        int timeoutSeconds
)
{ }

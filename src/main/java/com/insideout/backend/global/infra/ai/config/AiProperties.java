package com.insideout.backend.global.infra.ai.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "ai.service")
public record AiProperties(
        @NotBlank String baseUrl,
        @NotBlank String apiKeyHeader,
        @NotBlank String apiKey,
        @Min(1) int timeoutSeconds
) {
}

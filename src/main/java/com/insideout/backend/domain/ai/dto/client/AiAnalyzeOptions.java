package com.insideout.backend.domain.ai.dto.client;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AiAnalyzeOptions(
        @JsonProperty("model_version") String modelVersion
) {
}

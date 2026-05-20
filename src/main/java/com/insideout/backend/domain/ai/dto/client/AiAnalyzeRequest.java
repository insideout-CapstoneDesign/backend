package com.insideout.backend.domain.ai.dto.client;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public record AiAnalyzeRequest(
        @JsonProperty("floorplan_id") UUID floorplanId,
        @JsonProperty("image_url") String imageUrl,
        AiAnalyzeOptions options
) {
    private static final String DEFAULT_MODEL_VERSION = "v1.0";

    public static AiAnalyzeRequest of(UUID floorplanId, String imageUrl, String modelVersion) {
        String resolvedModelVersion = modelVersion != null ? modelVersion : DEFAULT_MODEL_VERSION;
        return new AiAnalyzeRequest(
                floorplanId,
                imageUrl,
                new AiAnalyzeOptions(resolvedModelVersion)
        );
    }
}

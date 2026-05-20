package com.insideout.backend.domain.ai.dto.client;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.UUID;

public record AiAnalyzeResponse(
        @JsonProperty("floorplan_id") UUID floorplanId,
        @JsonProperty("model_version") String modelVersion,
        @JsonProperty("processing_time_ms") int processingTimeMs,
        List<AiDetectionDTO> detections
) {
}

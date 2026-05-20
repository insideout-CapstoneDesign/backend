package com.insideout.backend.domain.ai.dto.client;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

public record AiDetectionDTO(
        @JsonProperty("detect_type") String detectType,
        double confidence,
        @JsonProperty("geom_px") Map<String, Object> geomPx,
        @JsonProperty("bbox_px") List<Double> bboxPx,
        String label,
        @JsonProperty("ocr_text") String ocrText
) {
}

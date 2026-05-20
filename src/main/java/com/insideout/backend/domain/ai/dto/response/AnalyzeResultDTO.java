package com.insideout.backend.domain.ai.dto.response;

import com.insideout.backend.domain.ai.entity.AiDetection;

import java.util.List;
import java.util.UUID;

public record AnalyzeResultDTO(
        UUID jobId,
        UUID floorplanId,
        String status,
        int detectionCount,
        List<DetectionViewDTO> detections
) {
    public static AnalyzeResultDTO of(UUID jobId, UUID floorplanId, String status, List<AiDetection> detections) {
        return new AnalyzeResultDTO(
                jobId,
                floorplanId,
                status,
                detections.size(),
                detections.stream().map(DetectionViewDTO::from).toList()
        );
    }
}

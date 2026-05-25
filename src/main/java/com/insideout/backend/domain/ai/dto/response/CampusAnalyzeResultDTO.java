package com.insideout.backend.domain.ai.dto.response;

import com.insideout.backend.domain.ai.entity.CampusAiDetection;

import java.util.List;
import java.util.UUID;

public record CampusAnalyzeResultDTO(
        UUID jobId,
        UUID campusMapId,
        String status,
        int detectionCount,
        List<CampusDetectionViewDTO> detections
) {
    public static CampusAnalyzeResultDTO of(UUID jobId, UUID campusMapId, String status, List<CampusAiDetection> detections) {
        return new CampusAnalyzeResultDTO(
                jobId,
                campusMapId,
                status,
                detections.size(),
                detections.stream().map(CampusDetectionViewDTO::from).toList()
        );
    }
}

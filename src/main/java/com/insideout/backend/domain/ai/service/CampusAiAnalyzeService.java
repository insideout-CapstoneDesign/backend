package com.insideout.backend.domain.ai.service;

import com.insideout.backend.domain.ai.client.AiClient;
import com.insideout.backend.domain.ai.dto.client.AiAnalyzeResponse;
import com.insideout.backend.domain.ai.dto.response.CampusAnalyzeResultDTO;
import com.insideout.backend.domain.ai.dto.response.CampusDetectionViewDTO;
import com.insideout.backend.domain.ai.entity.CampusAiJob;
import com.insideout.backend.domain.ai.exception.AiErrorCode;
import com.insideout.backend.domain.ai.exception.AiException;
import com.insideout.backend.domain.ai.repository.CampusAiDetectionRepository;
import com.insideout.backend.domain.ai.repository.CampusAiJobRepository;
import com.insideout.backend.domain.building.entity.CampusMap;
import com.insideout.backend.domain.building.repository.CampusMapRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CampusAiAnalyzeService {

    private static final String DEFAULT_MODEL_VERSION = "v1.0";

    private final AiClient aiClient;
    private final CampusAiAnalyzePersistenceService campusAiAnalyzePersistenceService;
    private final CampusMapRepository campusMapRepository;
    private final CampusAiDetectionRepository campusAiDetectionRepository;
    private final CampusAiJobRepository campusAiJobRepository;

    public CampusAnalyzeResultDTO analyze(UUID campusMapId, UUID tenantId) {
        CampusMap campusMap = campusMapRepository.findByIdAndTenantId(campusMapId, tenantId)
                .orElseThrow(() -> new AiException(AiErrorCode.FLOORPLAN_NOT_FOUND));

        CampusAiJob savedJob = campusAiAnalyzePersistenceService.createRunningJob(
                campusMap.getId(),
                tenantId,
                DEFAULT_MODEL_VERSION
        );

        try {
            AiAnalyzeResponse aiResponse = aiClient.analyze(
                    campusMap.getId(),
                    campusMap.getImageUrl(),
                    DEFAULT_MODEL_VERSION
            );
            return campusAiAnalyzePersistenceService.completeSuccess(
                    savedJob.getId(),
                    campusMap.getId(),
                    tenantId,
                    aiResponse
            );
        } catch (AiException e) {
            markFailureSafely(savedJob.getId(), e.getErrorCode().getMessage());
            throw e;
        } catch (Exception e) {
            log.error("캠퍼스 AI 분석 중 예상치 못한 오류 발생. Job ID: {}", savedJob.getId(), e);
            markFailureSafely(savedJob.getId(), AiErrorCode.AI_ANALYSIS_FAILED.getMessage());
            throw new AiException(AiErrorCode.AI_ANALYSIS_FAILED);
        }
    }

    private void markFailureSafely(UUID jobId, String errorMessage) {
        try {
            campusAiAnalyzePersistenceService.completeFailure(jobId, errorMessage);
        } catch (Exception e) {
            log.error("캠퍼스 AI 분석 실패 처리 중 오류 발생. Job ID: {}", jobId, e);
        }
    }

    @Transactional(readOnly = true)
    public List<CampusDetectionViewDTO> getDetections(UUID campusMapId, UUID tenantId) {
        CampusMap campusMap = campusMapRepository.findByIdAndTenantId(campusMapId, tenantId)
                .orElseThrow(() -> new AiException(AiErrorCode.FLOORPLAN_NOT_FOUND));

        CampusAiJob latestJob = campusAiJobRepository.findTopByCampusMap_IdAndTenantIdOrderByCreatedAtDesc(
                        campusMap.getId(),
                        tenantId
                )
                .orElse(null);

        if (latestJob == null) {
            return List.of();
        }

        return campusAiDetectionRepository.findByJob_IdOrderByIdAsc(latestJob.getId()).stream()
                .map(CampusDetectionViewDTO::from)
                .toList();
    }
}

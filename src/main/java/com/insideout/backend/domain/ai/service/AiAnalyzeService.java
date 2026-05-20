package com.insideout.backend.domain.ai.service;

import com.insideout.backend.domain.ai.client.AiClient;
import com.insideout.backend.domain.ai.dto.client.AiAnalyzeResponse;
import com.insideout.backend.domain.ai.dto.response.AnalyzeResultDTO;
import com.insideout.backend.domain.ai.dto.response.DetectionViewDTO;
import com.insideout.backend.domain.ai.entity.AiJob;
import com.insideout.backend.domain.ai.exception.AiErrorCode;
import com.insideout.backend.domain.ai.exception.AiException;
import com.insideout.backend.domain.ai.repository.AiDetectionRepository;
import com.insideout.backend.domain.ai.repository.AiJobRepository;
import com.insideout.backend.domain.building.facade.BuildingQueryFacade;
import com.insideout.backend.domain.building.entity.Floorplan;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AiAnalyzeService {

    private static final String DEFAULT_MODEL_VERSION = "v1.0";

    private final AiClient aiClient;
    private final AiAnalyzePersistenceService aiAnalyzePersistenceService;
    private final BuildingQueryFacade buildingQueryFacade;
    private final AiDetectionRepository aiDetectionRepository;
    private final AiJobRepository aiJobRepository;

    public AnalyzeResultDTO analyze(UUID floorplanId, UUID tenantId) {
        Floorplan floorplan = buildingQueryFacade.findFloorplanForTenant(floorplanId, tenantId)
                .orElseThrow(() -> new AiException(AiErrorCode.FLOORPLAN_NOT_FOUND));

        AiJob savedJob = aiAnalyzePersistenceService.createRunningJob(
                floorplan.getId(),
                tenantId,
                DEFAULT_MODEL_VERSION
        );

        try {
            AiAnalyzeResponse aiResponse = aiClient.analyze(
                    floorplan.getId(),
                    floorplan.getImageUrl(),
                    DEFAULT_MODEL_VERSION
            );
            return aiAnalyzePersistenceService.completeSuccess(
                    savedJob.getId(),
                    floorplan.getId(),
                    tenantId,
                    aiResponse
            );
        } catch (AiException e) {
            aiAnalyzePersistenceService.completeFailure(savedJob.getId(), e.getErrorCode().getMessage());
            throw e;
        } catch (Exception e) {
            aiAnalyzePersistenceService.completeFailure(savedJob.getId(), e.getMessage());
            throw new AiException(AiErrorCode.AI_ANALYSIS_FAILED);
        }
    }

    @Transactional(readOnly = true)
    public List<DetectionViewDTO> getDetections(UUID floorplanId, UUID tenantId) {
        Floorplan floorplan = buildingQueryFacade.findFloorplanForTenant(floorplanId, tenantId)
                .orElseThrow(() -> new AiException(AiErrorCode.FLOORPLAN_NOT_FOUND));

        AiJob latestJob = aiJobRepository.findTopByFloorplan_IdAndTenantIdOrderByStartedAtDesc(
                        floorplan.getId(),
                        tenantId
                )
                .orElse(null);

        if (latestJob == null) {
            return List.of();
        }

        return aiDetectionRepository.findByJob_IdOrderByIdAsc(latestJob.getId()).stream()
                .map(DetectionViewDTO::from)
                .toList();
    }
}

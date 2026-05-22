package com.insideout.backend.domain.ai.service;

import com.insideout.backend.domain.ai.converter.AiDetectionConverter;
import com.insideout.backend.domain.ai.dto.client.AiAnalyzeResponse;
import com.insideout.backend.domain.ai.dto.client.AiDetectionDTO;
import com.insideout.backend.domain.ai.dto.response.AnalyzeResultDTO;
import com.insideout.backend.domain.ai.entity.AiDetection;
import com.insideout.backend.domain.ai.entity.AiJob;
import com.insideout.backend.domain.ai.exception.AiErrorCode;
import com.insideout.backend.domain.ai.exception.AiException;
import com.insideout.backend.domain.ai.repository.AiDetectionRepository;
import com.insideout.backend.domain.ai.repository.AiJobRepository;
import com.insideout.backend.domain.building.entity.Floorplan;
import com.insideout.backend.domain.building.facade.BuildingQueryFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AiAnalyzePersistenceService {

    private final AiDetectionConverter aiDetectionConverter;
    private final BuildingQueryFacade buildingQueryFacade;
    private final AiDetectionRepository aiDetectionRepository;
    private final AiJobRepository aiJobRepository;

    @Transactional
    public AiJob createRunningJob(UUID floorplanId, UUID tenantId, String modelVersion) {
        Floorplan floorplan = buildingQueryFacade.findFloorplanForTenant(floorplanId, tenantId)
                .orElseThrow(() -> new AiException(AiErrorCode.FLOORPLAN_NOT_FOUND));

        // 기존 도면에 대한 이전 AI 감지 결과 및 작업 내역 삭제 (DB 누적 방지)
        aiDetectionRepository.deleteByFloorplanId(floorplanId);
        aiJobRepository.deleteByFloorplanId(floorplanId);

        AiJob job = AiJob.builder()
                .tenantId(tenantId)
                .floorplan(floorplan)
                .modelVersion(modelVersion)
                .build();
        job.markRunning();
        return aiJobRepository.save(job);
    }

    @Transactional(noRollbackFor = AiException.class)
    public AnalyzeResultDTO completeSuccess(
            UUID jobId,
            UUID floorplanId,
            UUID tenantId,
            AiAnalyzeResponse aiResponse
    ) {
        AiJob job = aiJobRepository.findById(jobId)
                .orElseThrow(() -> new AiException(AiErrorCode.AI_JOB_NOT_FOUND));
        Floorplan floorplan = buildingQueryFacade.findFloorplanForTenant(floorplanId, tenantId)
                .orElseThrow(() -> new AiException(AiErrorCode.FLOORPLAN_NOT_FOUND));
        if(!tenantId.equals(job.getTenantId()) || !floorplanId.equals(job.getFloorplan().getId())){
            throw new AiException(AiErrorCode.AI_JOB_NOT_FOUND);
        }

        List<AiDetection> detections = (aiResponse.detections() == null ? List.<AiDetectionDTO>of() : aiResponse.detections())
                .stream()
                .map(detectionDto -> aiDetectionConverter.toEntity(tenantId, job, floorplan, detectionDto))
                .toList();
        List<AiDetection> savedDetections = aiDetectionRepository.saveAll(detections);

        job.markSucceeded();
        return AnalyzeResultDTO.of(job.getId(), floorplan.getId(), job.getStatus(), savedDetections);
    }

    @Transactional(noRollbackFor = AiException.class)
    public void completeFailure(UUID jobId, String errorMessage) {
        AiJob job = aiJobRepository.findById(jobId)
                .orElseThrow(() -> new AiException(AiErrorCode.AI_JOB_NOT_FOUND));
        job.markFailed(errorMessage);
    }
}

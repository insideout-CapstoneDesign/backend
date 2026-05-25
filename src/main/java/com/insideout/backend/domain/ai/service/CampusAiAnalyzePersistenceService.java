package com.insideout.backend.domain.ai.service;

import com.insideout.backend.domain.ai.converter.AiDetectionConverter;
import com.insideout.backend.domain.ai.dto.client.AiAnalyzeResponse;
import com.insideout.backend.domain.ai.dto.client.AiDetectionDTO;
import com.insideout.backend.domain.ai.dto.response.CampusAnalyzeResultDTO;
import com.insideout.backend.domain.ai.entity.CampusAiDetection;
import com.insideout.backend.domain.ai.entity.CampusAiJob;
import com.insideout.backend.domain.ai.exception.AiErrorCode;
import com.insideout.backend.domain.ai.exception.AiException;
import com.insideout.backend.domain.ai.repository.CampusAiDetectionRepository;
import com.insideout.backend.domain.ai.repository.CampusAiJobRepository;
import com.insideout.backend.domain.building.entity.CampusMap;
import com.insideout.backend.domain.building.repository.CampusMapRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CampusAiAnalyzePersistenceService {

    private final AiDetectionConverter aiDetectionConverter;
    private final CampusMapRepository campusMapRepository;
    private final CampusAiDetectionRepository campusAiDetectionRepository;
    private final CampusAiJobRepository campusAiJobRepository;

    @Transactional
    public CampusAiJob createRunningJob(UUID campusMapId, UUID tenantId, String modelVersion) {
        CampusMap campusMap = campusMapRepository.findByIdAndTenantId(campusMapId, tenantId)
                .orElseThrow(() -> new AiException(AiErrorCode.FLOORPLAN_NOT_FOUND));

        // 기존 캠퍼스 맵에 대한 이전 AI 감지 결과 및 작업 내역 삭제 (DB 누적 방지)
        campusAiDetectionRepository.deleteByCampusMap_Id(campusMapId);
        campusAiJobRepository.deleteByCampusMap_Id(campusMapId);

        CampusAiJob job = CampusAiJob.builder()
                .tenantId(tenantId)
                .campusMap(campusMap)
                .modelVersion(modelVersion)
                .build();
        job.markRunning();
        return campusAiJobRepository.save(job);
    }

    @Transactional(noRollbackFor = AiException.class)
    public CampusAnalyzeResultDTO completeSuccess(
            UUID jobId,
            UUID campusMapId,
            UUID tenantId,
            AiAnalyzeResponse aiResponse
    ) {
        CampusAiJob job = campusAiJobRepository.findById(jobId)
                .orElseThrow(() -> new AiException(AiErrorCode.AI_JOB_NOT_FOUND));
        CampusMap campusMap = campusMapRepository.findByIdAndTenantId(campusMapId, tenantId)
                .orElseThrow(() -> new AiException(AiErrorCode.FLOORPLAN_NOT_FOUND));
        if (!tenantId.equals(job.getTenantId()) || !campusMapId.equals(job.getCampusMap().getId())) {
            throw new AiException(AiErrorCode.AI_JOB_NOT_FOUND);
        }

        List<CampusAiDetection> detections = (aiResponse.detections() == null ? List.<AiDetectionDTO>of() : aiResponse.detections())
                .stream()
                .map(detectionDto -> aiDetectionConverter.toCampusEntity(tenantId, job, campusMap, detectionDto))
                .toList();
        List<CampusAiDetection> savedDetections = campusAiDetectionRepository.saveAll(detections);

        job.markSucceeded();
        return CampusAnalyzeResultDTO.of(job.getId(), campusMap.getId(), job.getStatus(), savedDetections);
    }

    @Transactional(noRollbackFor = AiException.class)
    public void completeFailure(UUID jobId, String errorMessage) {
        CampusAiJob job = campusAiJobRepository.findById(jobId)
                .orElseThrow(() -> new AiException(AiErrorCode.AI_JOB_NOT_FOUND));
        job.markFailed(errorMessage);
    }
}

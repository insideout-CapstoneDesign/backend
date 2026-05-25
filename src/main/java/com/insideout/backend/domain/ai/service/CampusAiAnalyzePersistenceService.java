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

        // 기존 캠퍼스 맵에 대한 터미널 상태(Succeeded, Failed)의 AI 작업 내역만 안전하게 삭제 (진행 중인 작업 보호)
        // DB의 ON DELETE CASCADE 제약 조건으로 인해 연관된 탐지 결과(CampusAiDetection)도 함께 자동 삭제됨
        campusAiJobRepository.deleteTerminalJobsByCampusMapId(campusMapId);

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

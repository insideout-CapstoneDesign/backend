package com.insideout.backend.domain.ai.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.insideout.backend.domain.ai.client.AiClient;
import com.insideout.backend.domain.ai.entity.CampusAiJob;
import com.insideout.backend.domain.ai.exception.AiErrorCode;
import com.insideout.backend.domain.ai.exception.AiException;
import com.insideout.backend.domain.building.entity.CampusMap;
import com.insideout.backend.domain.building.repository.CampusMapRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class CampusAiAnalyzeServiceTest {

    @Mock
    private AiClient aiClient;

    @Mock
    private CampusAiAnalyzePersistenceService campusAiAnalyzePersistenceService;

    @Mock
    private CampusMapRepository campusMapRepository;

    @InjectMocks
    private CampusAiAnalyzeService campusAiAnalyzeService;

    @Test
    void analyze_whenAiClientThrowsException_completeFailureThrowsException_stillPropagatesOriginalException() {
        // Arrange
        UUID campusMapId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();

        CampusMap campusMap = mock(CampusMap.class);
        when(campusMap.getId()).thenReturn(campusMapId);
        when(campusMap.getImageUrl()).thenReturn("http://image.url");
        when(campusMapRepository.findByIdAndTenantId(campusMapId, tenantId)).thenReturn(Optional.of(campusMap));

        CampusAiJob runningJob = mock(CampusAiJob.class);
        when(runningJob.getId()).thenReturn(jobId);
        when(campusAiAnalyzePersistenceService.createRunningJob(eq(campusMapId), eq(tenantId), anyString()))
                .thenReturn(runningJob);

        // AI client throws an exception
        when(aiClient.analyze(eq(campusMapId), eq("http://image.url"), anyString()))
                .thenThrow(new AiException(AiErrorCode.AI_ANALYSIS_FAILED));

        // completeFailure also throws a RuntimeException (e.g. database error)
        doThrow(new RuntimeException("DB Connection Timeout"))
                .when(campusAiAnalyzePersistenceService).completeFailure(eq(jobId), anyString());

        // Act & Assert
        // We assert that the original exception (AiException with AI_ANALYSIS_FAILED) is thrown,
        // and NOT the "DB Connection Timeout" exception from completeFailure.
        assertThatThrownBy(() -> campusAiAnalyzeService.analyze(campusMapId, tenantId))
                .isInstanceOf(AiException.class)
                .extracting(ex -> ((AiException) ex).getErrorCode())
                .isEqualTo(AiErrorCode.AI_ANALYSIS_FAILED);

        // Verify that completeFailure was indeed called
        verify(campusAiAnalyzePersistenceService).completeFailure(eq(jobId), eq(AiErrorCode.AI_ANALYSIS_FAILED.getMessage()));
    }
}

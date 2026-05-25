package com.insideout.backend.domain.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.insideout.backend.domain.ai.entity.CampusAiJob;
import com.insideout.backend.domain.ai.repository.CampusAiDetectionRepository;
import com.insideout.backend.domain.ai.repository.CampusAiJobRepository;
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
class CampusAiAnalyzePersistenceServiceTest {

    @Mock
    private CampusMapRepository campusMapRepository;

    @Mock
    private CampusAiDetectionRepository campusAiDetectionRepository;

    @Mock
    private CampusAiJobRepository campusAiJobRepository;

    @InjectMocks
    private CampusAiAnalyzePersistenceService campusAiAnalyzePersistenceService;

    @Test
    void createRunningJob_succeedsAndDeletesOnlyTerminalJobs() {
        // Arrange
        UUID campusMapId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        String modelVersion = "v1.0";

        CampusMap campusMap = mock(CampusMap.class);
        when(campusMap.getTenantId()).thenReturn(tenantId);
        when(campusMapRepository.findByIdAndTenantId(campusMapId, tenantId)).thenReturn(Optional.of(campusMap));

        CampusAiJob mockJob = CampusAiJob.builder()
                .tenantId(tenantId)
                .campusMap(campusMap)
                .modelVersion(modelVersion)
                .build();
        
        when(campusAiJobRepository.save(any(CampusAiJob.class))).thenReturn(mockJob);

        // Act
        CampusAiJob result = campusAiAnalyzePersistenceService.createRunningJob(campusMapId, tenantId, modelVersion);

        // Assert
        assertThat(result).isNotNull();
        // Verify deleteTerminalJobsByCampusMapId was called instead of blanket deleteByCampusMap_Id
        verify(campusAiJobRepository).deleteTerminalJobsByCampusMapId(campusMapId);
        verify(campusAiDetectionRepository, never()).deleteByCampusMap_Id(any());
        verify(campusAiJobRepository).save(any(CampusAiJob.class));
    }
}

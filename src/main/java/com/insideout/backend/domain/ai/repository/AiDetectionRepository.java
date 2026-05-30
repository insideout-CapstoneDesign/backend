package com.insideout.backend.domain.ai.repository;

import com.insideout.backend.domain.ai.entity.AiDetection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AiDetectionRepository extends JpaRepository<AiDetection, UUID> {

    List<AiDetection> findByFloorplanIdAndTenantId(UUID floorplanId, UUID tenantId);

    List<AiDetection> findByJob_IdOrderByIdAsc(UUID jobId);

    void deleteByFloorplanId(UUID floorplanId);

    @Query("""
            select distinct d.floorplan.id
            from AiDetection d
            where d.tenantId = :tenantId
              and d.floorplan.id in :floorplanIds
            """)
    List<UUID> findAnalyzedFloorplanIdsByTenantIdAndFloorplanIds(UUID tenantId, List<UUID> floorplanIds);
}

package com.insideout.backend.domain.ai.repository;

import com.insideout.backend.domain.ai.entity.CampusAiJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CampusAiJobRepository extends JpaRepository<CampusAiJob, UUID> {
    Optional<CampusAiJob> findTopByCampusMap_IdAndTenantIdOrderByCreatedAtDesc(UUID campusMapId, UUID tenantId);
    void deleteByCampusMap_Id(UUID campusMapId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM CampusAiJob j WHERE j.campusMap.id = :campusMapId AND j.status IN ('succeeded', 'failed')")
    void deleteTerminalJobsByCampusMapId(@Param("campusMapId") UUID campusMapId);
}

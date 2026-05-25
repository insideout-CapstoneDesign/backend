package com.insideout.backend.domain.ai.repository;

import com.insideout.backend.domain.ai.entity.CampusAiJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CampusAiJobRepository extends JpaRepository<CampusAiJob, UUID> {
    Optional<CampusAiJob> findTopByCampusMap_IdAndTenantIdOrderByStartedAtDescIdDesc(UUID campusMapId, UUID tenantId);
    void deleteByCampusMap_Id(UUID campusMapId);
}

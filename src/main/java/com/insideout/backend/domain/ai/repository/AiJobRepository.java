package com.insideout.backend.domain.ai.repository;

import com.insideout.backend.domain.ai.entity.AiJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AiJobRepository extends JpaRepository<AiJob, UUID> {

    Optional<AiJob> findTopByFloorplan_IdAndTenantIdOrderByStartedAtDescIdDesc(UUID floorplanId, UUID tenantId);

    void deleteByFloorplanId(UUID floorplanId);
}

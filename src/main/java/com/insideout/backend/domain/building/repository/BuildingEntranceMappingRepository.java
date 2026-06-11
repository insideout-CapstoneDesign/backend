package com.insideout.backend.domain.building.repository;

import com.insideout.backend.domain.building.entity.BuildingEntranceMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BuildingEntranceMappingRepository extends JpaRepository<BuildingEntranceMapping, UUID> {

    List<BuildingEntranceMapping> findAllByTenantIdAndBuildingIdAndMapVersionIdOrderByCreatedAtAsc(UUID tenantId, UUID buildingId, UUID mapVersionId);

    Optional<BuildingEntranceMapping> findByTenantIdAndBuildingIdAndMapVersionIdAndEntranceNodeId(UUID tenantId, UUID buildingId, UUID mapVersionId, UUID entranceNodeId);

    Optional<BuildingEntranceMapping> findByTenantIdAndCampusIdAndMapVersionIdAndCampusGateId(UUID tenantId, UUID campusId, UUID mapVersionId, String campusGateId);
}

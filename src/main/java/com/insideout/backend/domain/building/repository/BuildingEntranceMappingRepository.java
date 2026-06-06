package com.insideout.backend.domain.building.repository;

import com.insideout.backend.domain.building.entity.BuildingEntranceMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BuildingEntranceMappingRepository extends JpaRepository<BuildingEntranceMapping, UUID> {

    List<BuildingEntranceMapping> findAllByTenantIdAndBuildingIdOrderByCreatedAtAsc(UUID tenantId, UUID buildingId);

    Optional<BuildingEntranceMapping> findByTenantIdAndBuildingIdAndEntranceNodeId(UUID tenantId, UUID buildingId, UUID entranceNodeId);

    Optional<BuildingEntranceMapping> findByTenantIdAndCampusIdAndCampusGateId(UUID tenantId, UUID campusId, String campusGateId);
}

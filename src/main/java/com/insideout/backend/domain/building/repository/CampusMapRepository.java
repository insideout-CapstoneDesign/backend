package com.insideout.backend.domain.building.repository;

import com.insideout.backend.domain.building.entity.CampusMap;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CampusMapRepository extends JpaRepository<CampusMap, UUID> {

    Optional<CampusMap> findByCampusIdAndIsCurrentTrue(UUID campusId);

    Optional<CampusMap> findByIdAndTenantId(UUID id, UUID tenantId);

    @Modifying
    @Query("UPDATE CampusMap cm SET cm.isCurrent = false WHERE cm.campus.id = :campusId")
    void deactivateCurrentMapsByCampusId(@Param("campusId") UUID campusId);
}

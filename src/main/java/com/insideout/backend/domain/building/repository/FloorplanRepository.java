package com.insideout.backend.domain.building.repository;

import com.insideout.backend.domain.building.entity.Floorplan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface FloorplanRepository extends JpaRepository<Floorplan, UUID> {

    Optional<Floorplan> findByFloorIdAndIsCurrentTrue(UUID floorId);

    Optional<Floorplan> findByIdAndTenantId(UUID id, UUID tenantId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Floorplan f SET f.isCurrent = false WHERE f.floor.id = :floorId AND f.isCurrent = true")
    int deactivateCurrentFloorplansByFloorId(@Param("floorId") UUID floorId);
}

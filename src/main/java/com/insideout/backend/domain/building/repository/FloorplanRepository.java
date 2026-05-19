package com.insideout.backend.domain.building.repository;

import com.insideout.backend.domain.building.entity.Floorplan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface FloorplanRepository extends JpaRepository<Floorplan, UUID> {

    Optional<Floorplan> findByFloorIdAndIsCurrentTrue(UUID floorId);
}

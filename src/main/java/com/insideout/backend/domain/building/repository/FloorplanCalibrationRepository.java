package com.insideout.backend.domain.building.repository;

import com.insideout.backend.domain.building.entity.FloorplanCalibration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface FloorplanCalibrationRepository extends JpaRepository<FloorplanCalibration, UUID> {
    java.util.Optional<FloorplanCalibration> findByFloorplanId(UUID floorplanId);
}

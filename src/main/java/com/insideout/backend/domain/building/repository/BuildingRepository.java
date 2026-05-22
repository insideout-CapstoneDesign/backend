package com.insideout.backend.domain.building.repository;

import com.insideout.backend.domain.building.entity.Building;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BuildingRepository extends JpaRepository<Building, UUID> {

    Optional<Building> findByIdAndCampusIsNotNull(UUID id);

    List<Building> findByTenant_IdOrderByCreatedAtDesc(UUID tenantId);
}

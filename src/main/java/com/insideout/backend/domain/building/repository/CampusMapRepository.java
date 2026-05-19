package com.insideout.backend.domain.building.repository;

import com.insideout.backend.domain.building.entity.CampusMap;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CampusMapRepository extends JpaRepository<CampusMap, UUID> {

    Optional<CampusMap> findByCampusIdAndIsCurrentTrue(UUID campusId);
}

package com.insideout.backend.domain.map.repository;

import com.insideout.backend.domain.map.entity.MapVersion;
import com.insideout.backend.domain.map.enums.MapType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface MapVersionRepository extends JpaRepository<MapVersion, UUID> {

    Optional<MapVersion> findFirstByBuildingIdAndMapTypeAndStatus(UUID buildingId, MapType mapType, String status);

    Optional<MapVersion> findFirstByBuildingIdAndMapTypeAndStatusOrderByCreatedAtDesc(UUID buildingId, MapType mapType, String status);

    Optional<MapVersion> findFirstByBuildingIdAndMapTypeOrderByCreatedAtDesc(UUID buildingId, MapType mapType);

    Optional<MapVersion> findFirstByCampusIdAndMapTypeAndStatus(UUID campusId, MapType mapType, String status);

    Optional<MapVersion> findFirstByCampusIdAndMapTypeAndStatusOrderByCreatedAtDesc(UUID campusId, MapType mapType, String status);
}

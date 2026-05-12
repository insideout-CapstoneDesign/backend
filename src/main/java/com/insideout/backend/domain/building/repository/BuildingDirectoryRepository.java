package com.insideout.backend.domain.building.repository;

import com.insideout.backend.domain.building.entity.BuildingDirectory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface BuildingDirectoryRepository extends JpaRepository<BuildingDirectory, UUID> {

    Optional<BuildingDirectory> findByIdAndIsPublicTrue(UUID id);

    @Query(value = """
            SELECT *
            FROM building_directory
            WHERE is_public = true
              AND centroid IS NOT NULL
              AND ST_DWithin(
                    centroid,
                    ST_SetSRID(ST_MakePoint(:x, :y), 4326)::geography,
                    :radiusMeters
              )
            ORDER BY ST_Distance(
                    centroid,
                    ST_SetSRID(ST_MakePoint(:x, :y), 4326)::geography
              )
            LIMIT 1
            """, nativeQuery = true)
    Optional<BuildingDirectory> findNearestPublicBuilding(
            @Param("x") double x,
            @Param("y") double y,
            @Param("radiusMeters") double radiusMeters
    );
}

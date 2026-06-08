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

    Optional<BuildingDirectory> findByIdAndTenant_Id(UUID id, UUID tenantId);

    Optional<BuildingDirectory> findByIdAndIsPublicTrue(UUID id);

    @Query(value = """
            WITH target AS (
                SELECT ST_SetSRID(ST_MakePoint(:x, :y), 4326) AS geom
            )
            SELECT *
            FROM building_directory bd
            CROSS JOIN target t
            WHERE bd.is_public = true
              AND bd.centroid IS NOT NULL
              AND (
                    (bd.bbox IS NOT NULL AND ST_DWithin(
                        bd.bbox,
                        t.geom::geography,
                        :radiusMeters
                    ))
                 OR ST_DWithin(
                        bd.centroid,
                        t.geom::geography,
                        :radiusMeters
                    )
              )
            ORDER BY
                CASE
                    WHEN bd.bbox IS NOT NULL AND ST_Covers(bd.bbox::geometry, t.geom) THEN 0
                    ELSE 1
                END,
                CASE
                    WHEN bd.bbox IS NOT NULL THEN ST_Distance(bd.bbox, t.geom::geography)
                    ELSE ST_Distance(bd.centroid, t.geom::geography)
                END,
                COALESCE(ST_Area(bd.bbox::geometry), 0),
                bd.id
            LIMIT 1
            """, nativeQuery = true)
    Optional<BuildingDirectory> findNearestPublicBuilding(
            @Param("x") double x,
            @Param("y") double y,
            @Param("radiusMeters") double radiusMeters
    );
}

package com.insideout.backend.domain.map.repository;

import com.insideout.backend.domain.map.entity.Node;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NodeRepository extends JpaRepository<Node, UUID> {

    @Query(value = """
            SELECT n.*
            FROM node n
            JOIN map_version mv ON mv.id = n.map_version_id
            WHERE mv.building_id = :buildingId
              AND n.kind = 'entrance'
              AND n.geom_wgs84 IS NOT NULL
            ORDER BY ST_Distance(
                    n.geom_wgs84,
                    ST_SetSRID(ST_MakePoint(:x, :y), 4326)::geography
              )
            LIMIT 1
            """, nativeQuery = true)
    Optional<Node> findNearestEntranceByBuildingId(
            @Param("buildingId") UUID buildingId,
            @Param("x") double x,
            @Param("y") double y
    );

    List<Node> findByMapVersionId(UUID mapVersionId);

    List<Node> findByMapVersion_Building_IdAndKindCodeOrderByCreatedAtAsc(UUID buildingId, String kindCode);

    long countByMapVersion_Building_IdAndKindCode(UUID buildingId, String kindCode);

    @Query(value = """
            SELECT n.*
            FROM node n
            JOIN map_version mv ON mv.id = n.map_version_id
            WHERE mv.campus_id = :campusId
              AND mv.map_type = 'CAMPUS'
              AND mv.status = 'published'
              AND n.geom_wgs84 IS NOT NULL
            ORDER BY ST_Distance(
                    n.geom_wgs84,
                    ST_SetSRID(ST_MakePoint(:x, :y), 4326)::geography
              )
            LIMIT 1
            """, nativeQuery = true)
    Optional<Node> findNearestPublishedCampusNode(
            @Param("campusId") UUID campusId,
            @Param("x") double x,
            @Param("y") double y
    );

    List<Node> findByMapVersionIdAndFloorId(UUID mapVersionId, UUID floorId);
}

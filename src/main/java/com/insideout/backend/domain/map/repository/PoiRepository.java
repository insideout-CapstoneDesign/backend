package com.insideout.backend.domain.map.repository;

import com.insideout.backend.domain.map.entity.Poi;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PoiRepository extends JpaRepository<Poi, UUID> {

    Optional<Poi> findByPublicId(Long publicId);

    @Query("""
            select p
            from Poi p
                join fetch p.floor f
                join fetch f.building b
                join fetch p.mapVersion mv
            where p.publicId = :publicId
              and mv.mapType = com.insideout.backend.domain.map.enums.MapType.BUILDING
              and mv.status = 'published'
            """)
    Optional<Poi> findPublishedByPublicId(@Param("publicId") Long publicId);

    Optional<Poi> findFirstByExternalApiId(String externalApiId);

    List<Poi> findByAnchorNodeIdIn(List<UUID> anchorNodeIds);

    List<Poi> findByMapVersionIdAndFloorId(UUID mapVersionId, UUID floorId);

    List<Poi> findByMapVersionId(UUID mapVersionId);

    @Query("select distinct p.floor.id from Poi p where p.mapVersion.id = :mapVersionId")
    List<UUID> findFloorIdsWithPois(@Param("mapVersionId") UUID mapVersionId);

    @Modifying
    @Query("delete from Poi p where p.mapVersion.id = :mapVersionId and p.floor.id = :floorId")
    void deleteByMapVersionIdAndFloorId(
            @Param("mapVersionId") UUID mapVersionId,
            @Param("floorId") UUID floorId
    );

    @Query("""
            select p
            from Poi p
                join fetch p.floor f
                join fetch f.building b
                join fetch p.mapVersion mv
            where p.id = :id
            """)
    Optional<Poi> findByIdWithFloorAndMapVersion(@Param("id") UUID id);

    @Query("""
            select p
            from Poi p
                join fetch p.floor f
                join fetch f.building b
                join fetch p.mapVersion mv
            where p.externalApiId = :externalApiId
            """)
    Optional<Poi> findFirstByExternalApiIdWithFloorAndMapVersion(@Param("externalApiId") String externalApiId);

    @Query("""
            select p
            from Poi p
                join fetch p.floor f
                join fetch f.building b
            where p.mapVersion.id = :mapVersionId
            order by f.level desc, p.name asc, p.id asc
            """)
    List<Poi> findAllByMapVersionIdWithFloor(@Param("mapVersionId") UUID mapVersionId);

    @Query(value = """
            SELECT
                b.id AS buildingId,
                p.public_id AS poiId,
                p.name AS name,
                b.address AS address,
                b.name AS buildingName,
                p.external_api_id AS externalApiId,
                CAST(ST_Y(COALESCE(bd.centroid::geometry, ST_Centroid(bd.bbox::geometry), ST_Centroid(b.footprint::geometry))) AS double precision) AS lat,
                CAST(ST_X(COALESCE(bd.centroid::geometry, ST_Centroid(bd.bbox::geometry), ST_Centroid(b.footprint::geometry))) AS double precision) AS lng
            FROM poi p
                JOIN floor f ON f.id = p.floor_id
                JOIN building b ON b.id = f.building_id
                JOIN map_version mv ON mv.id = p.map_version_id
                LEFT JOIN building_directory bd
                    ON bd.id = b.id
                   AND bd.is_public = true
            WHERE p.external_api_id IN (:externalApiIds)
              AND mv.status = 'published'
            """, nativeQuery = true)
    List<RegisteredPoiSearchProjection> findRegisteredPlacesByExternalApiIds(@Param("externalApiIds") Collection<String> externalApiIds);
}

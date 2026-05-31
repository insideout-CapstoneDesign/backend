package com.insideout.backend.domain.building.repository;

import com.insideout.backend.domain.building.entity.Building;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BuildingRepository extends JpaRepository<Building, UUID> {

    Optional<Building> findByIdAndCampusIsNotNull(UUID id);

    List<Building> findByTenant_IdOrderByCreatedAtDesc(UUID tenantId);

    @Query(value = """
            SELECT
                b.id AS id,
                b.name AS name,
                b.address AS address,
                CAST(ST_Y(ST_Centroid(b.footprint::geometry)) AS double precision) AS lat,
                CAST(ST_X(ST_Centroid(b.footprint::geometry)) AS double precision) AS lng,
                b.external_api_id AS externalApiId
            FROM building b
            WHERE b.name ILIKE CONCAT('%', :query, '%')
               OR b.address ILIKE CONCAT('%', :query, '%')
            ORDER BY b.created_at DESC
            """, nativeQuery = true)
    List<BuildingSearchProjection> searchRegisteredPlaces(@Param("query") String query);

    @Query(value = """
            SELECT
                b.id AS id,
                b.name AS name,
                b.address AS address,
                CAST(ST_Y(ST_Centroid(b.footprint::geometry)) AS double precision) AS lat,
                CAST(ST_X(ST_Centroid(b.footprint::geometry)) AS double precision) AS lng,
                b.external_api_id AS externalApiId
            FROM building b
            WHERE b.name ILIKE CONCAT('%', :query, '%')
               OR b.address ILIKE CONCAT('%', :query, '%')
            ORDER BY b.created_at DESC, b.id DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<BuildingSearchProjection> searchRegisteredPlacesLimited(
            @Param("query") String query,
            @Param("limit") int limit
    );

    @Query(value = """
            SELECT
                b.id AS id,
                b.name AS name,
                b.address AS address,
                CAST(ST_Y(ST_Centroid(b.footprint::geometry)) AS double precision) AS lat,
                CAST(ST_X(ST_Centroid(b.footprint::geometry)) AS double precision) AS lng,
                b.external_api_id AS externalApiId
            FROM building b
            WHERE b.external_api_id IN (:externalApiIds)
            """, nativeQuery = true)
    List<BuildingSearchProjection> findRegisteredPlacesByExternalApiIds(@Param("externalApiIds") Collection<String> externalApiIds);

    @Query(value = """
            SELECT
                b.id AS id,
                b.name AS name,
                b.address AS address,
                CAST(ST_Y(ST_Centroid(b.footprint::geometry)) AS double precision) AS lat,
                CAST(ST_X(ST_Centroid(b.footprint::geometry)) AS double precision) AS lng,
                b.external_api_id AS externalApiId
            FROM building b
            WHERE b.footprint IS NOT NULL
            ORDER BY b.created_at DESC, b.id DESC
            """,
            countQuery = """
                    SELECT COUNT(*)
                    FROM building b
                    WHERE b.footprint IS NOT NULL
                    """,
            nativeQuery = true)
    Page<BuildingSearchProjection> findRegisteredPlacesForIndexing(Pageable pageable);

    @Query(value = """
            SELECT
                b.id AS id,
                b.name AS name,
                b.address AS address,
                CAST(ST_Y(ST_Centroid(b.footprint::geometry)) AS double precision) AS lat,
                CAST(ST_X(ST_Centroid(b.footprint::geometry)) AS double precision) AS lng,
                b.external_api_id AS externalApiId
            FROM building b
            WHERE b.footprint IS NOT NULL
              AND ST_DWithin(
                    b.footprint,
                    CAST(ST_SetSRID(ST_MakePoint(:lng, :lat), 4326) AS geography),
                    :radius
              )
            ORDER BY ST_Distance(
                    b.footprint,
                    CAST(ST_SetSRID(ST_MakePoint(:lng, :lat), 4326) AS geography)
              )
            LIMIT 1
            """, nativeQuery = true)
    Optional<BuildingSearchProjection> findNearestRegisteredPlace(
            @Param("lat") double lat,
            @Param("lng") double lng,
            @Param("radius") int radius
    );
}

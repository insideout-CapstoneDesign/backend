package com.insideout.backend.domain.map.repository;

import com.insideout.backend.domain.map.entity.Obstacle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ObstacleRepository extends JpaRepository<Obstacle, UUID> {

    @Query(value = """
            SELECT *
            FROM obstacle
            WHERE building_id = :buildingId
              AND (active_from IS NULL OR active_from <= now())
              AND (active_to IS NULL OR active_to >= now())
            """, nativeQuery = true)
    List<Obstacle> findActiveByBuildingId(@Param("buildingId") UUID buildingId);

    @Query(value = """
            SELECT *
            FROM obstacle
            WHERE campus_id = :campusId
              AND (active_from IS NULL OR active_from <= now())
              AND (active_to IS NULL OR active_to >= now())
            """, nativeQuery = true)
    List<Obstacle> findActiveByCampusId(@Param("campusId") UUID campusId);
}

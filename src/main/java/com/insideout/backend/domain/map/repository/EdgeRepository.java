package com.insideout.backend.domain.map.repository;

import com.insideout.backend.domain.map.entity.Edge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface EdgeRepository extends JpaRepository<Edge, UUID> {

    List<Edge> findByMapVersionId(UUID mapVersionId);

    @Query("""
            select e
            from Edge e
            where e.mapVersion.id = :mapVersionId
              and e.fromNode.floor.id = :floorId
              and e.toNode.floor.id = :floorId
            """)
    List<Edge> findByMapVersionIdAndFloorId(
            @Param("mapVersionId") UUID mapVersionId,
            @Param("floorId") UUID floorId
    );
}

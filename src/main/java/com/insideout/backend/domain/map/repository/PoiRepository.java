package com.insideout.backend.domain.map.repository;

import com.insideout.backend.domain.map.entity.Poi;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;
import java.util.List;

@Repository
public interface PoiRepository extends JpaRepository<Poi, UUID> {

    Optional<Poi> findByPublicId(Long publicId);

    List<Poi> findByAnchorNodeIdIn(List<UUID> anchorNodeIds);

    List<Poi> findByMapVersionIdAndFloorId(UUID mapVersionId, UUID floorId);

    List<Poi> findByMapVersionId(UUID mapVersionId);

    @Modifying
    @Query("delete from Poi p where p.mapVersion.id = :mapVersionId and p.floor.id = :floorId")
    void deleteByMapVersionIdAndFloorId(
            @Param("mapVersionId") UUID mapVersionId,
            @Param("floorId") UUID floorId
    );
}

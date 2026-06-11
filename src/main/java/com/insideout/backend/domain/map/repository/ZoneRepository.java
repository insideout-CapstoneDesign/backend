package com.insideout.backend.domain.map.repository;

import com.insideout.backend.domain.map.entity.Zone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ZoneRepository extends JpaRepository<Zone, UUID> {

    List<Zone> findByMapVersionIdAndFloorId(UUID mapVersionId, UUID floorId);

    List<Zone> findByMapVersionId(UUID mapVersionId);

    @Query("select distinct z.floor.id from Zone z where z.mapVersion.id = :mapVersionId")
    List<UUID> findFloorIdsWithZones(@Param("mapVersionId") UUID mapVersionId);

    @Modifying
    @Query("delete from Zone z where z.mapVersion.id = :mapVersionId and z.floor.id = :floorId")
    void deleteByMapVersionIdAndFloorId(
            @Param("mapVersionId") UUID mapVersionId,
            @Param("floorId") UUID floorId
    );
}

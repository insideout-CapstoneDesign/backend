package com.insideout.backend.domain.map.repository;

import com.insideout.backend.domain.map.entity.FloorplanObject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface FloorplanObjectRepository extends JpaRepository<FloorplanObject, UUID> {

    List<FloorplanObject> findByMapVersionIdAndFloorId(UUID mapVersionId, UUID floorId);
}

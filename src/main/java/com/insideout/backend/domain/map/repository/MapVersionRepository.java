package com.insideout.backend.domain.map.repository;

import com.insideout.backend.domain.map.entity.MapVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface MapVersionRepository extends JpaRepository<MapVersion, UUID> {
}

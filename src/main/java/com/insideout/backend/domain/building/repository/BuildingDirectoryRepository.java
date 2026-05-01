package com.insideout.backend.domain.building.repository;

import com.insideout.backend.domain.building.entity.BuildingDirectory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface BuildingDirectoryRepository extends JpaRepository<BuildingDirectory, UUID> {
}

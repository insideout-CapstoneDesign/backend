package com.insideout.backend.domain.building.repository;

import com.insideout.backend.domain.building.entity.Campus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CampusRepository extends JpaRepository<Campus, UUID> {
    Optional<Campus> findByIdAndTenant_Id(UUID id, UUID tenantId);
}

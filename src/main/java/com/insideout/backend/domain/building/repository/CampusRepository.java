package com.insideout.backend.domain.building.repository;

import com.insideout.backend.domain.building.entity.Campus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CampusRepository extends JpaRepository<Campus, UUID> {
    Optional<Campus> findByIdAndTenant_Id(UUID id, UUID tenantId);

    List<Campus> findAllByTenant_IdOrderByCreatedAtDesc(UUID tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Campus c WHERE c.id = :id AND c.tenant.id = :tenantId")
    Optional<Campus> findByIdAndTenant_IdForUpdate(@Param("id") UUID id, @Param("tenantId") UUID tenantId);
}

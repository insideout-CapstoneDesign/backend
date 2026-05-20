package com.insideout.backend.domain.tenant.repository;

import com.insideout.backend.domain.tenant.entity.TenantMembership;
import com.insideout.backend.domain.tenant.entity.TenantMembershipId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TenantMembershipRepository extends JpaRepository<TenantMembership, TenantMembershipId> {

    Optional<TenantMembership> findFirstByUser_Id(UUID userId);
}

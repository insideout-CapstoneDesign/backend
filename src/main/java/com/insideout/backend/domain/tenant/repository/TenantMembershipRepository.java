package com.insideout.backend.domain.tenant.repository;

import com.insideout.backend.domain.tenant.entity.TenantMembership;
import com.insideout.backend.domain.tenant.entity.TenantMembershipId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TenantMembershipRepository extends JpaRepository<TenantMembership, TenantMembershipId> {
}

package com.insideout.backend.domain.tenant.repository;

import com.insideout.backend.domain.tenant.entity.TenantMembership;
import com.insideout.backend.domain.tenant.entity.TenantMembershipId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TenantMembershipRepository extends JpaRepository<TenantMembership, TenantMembershipId> {

    /**
     * 특정 유저가 소속된 모든 테넌트 멤버십을 조회합니다.
     * (대시보드에서 테넌트 목록을 표시할 때 사용)
     */
    List<TenantMembership> findAllByUser_Id(UUID userId);

    /**
     * 특정 유저가 특정 테넌트에 소속되어 있는지 확인합니다.
     * (API 요청 시 권한 검증에 사용)
     */
    boolean existsByUser_IdAndTenant_Id(UUID userId, UUID tenantId);

    /**
     * 특정 유저가 특정 테넌트에 소속된 멤버십 정보를 조회합니다.
     */
    Optional<TenantMembership> findByUser_IdAndTenant_Id(UUID userId, UUID tenantId);
}

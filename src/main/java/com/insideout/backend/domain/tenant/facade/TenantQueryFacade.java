package com.insideout.backend.domain.tenant.facade;

import com.insideout.backend.domain.tenant.entity.TenantMembership;
import com.insideout.backend.domain.tenant.repository.TenantMembershipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TenantQueryFacade {

    private final TenantMembershipRepository tenantMembershipRepository;

    /**
     * 유저가 소속된 모든 테넌트 멤버십 목록을 반환합니다.
     * (대시보드 테넌트 선택 화면에서 사용)
     */
    public List<TenantMembership> findAllMembershipsByUserId(UUID userId) {
        if (userId == null) {
            return List.of();
        }
        return tenantMembershipRepository.findAllByUser_Id(userId);
    }

    /**
     * 유저가 특정 테넌트에 소속되어 있는지 검증합니다.
     * (프론트에서 tenantId를 보낼 때 권한 확인용)
     */
    public boolean isUserMemberOfTenant(UUID userId, UUID tenantId) {
        if (userId == null || tenantId == null) {
            return false;
        }
        return tenantMembershipRepository.existsByUser_IdAndTenant_Id(userId, tenantId);
    }
}

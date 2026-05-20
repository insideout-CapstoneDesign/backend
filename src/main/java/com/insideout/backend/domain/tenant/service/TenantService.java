package com.insideout.backend.domain.tenant.service;

import com.insideout.backend.domain.tenant.dto.TenantSummaryDTO;
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
public class TenantService {

    private final TenantMembershipRepository tenantMembershipRepository;

    /**
     * 유저가 소속된 모든 테넌트 목록을 반환합니다.
     */
    public List<TenantSummaryDTO> getMyTenants(UUID userId) {
        List<TenantMembership> memberships = tenantMembershipRepository.findAllByUser_Id(userId);

        return memberships.stream()
                .map(m -> TenantSummaryDTO.from(m.getTenant(), m.getRole(), m.getJoinedAt()))
                .toList();
    }
}

package com.insideout.backend.domain.tenant.service;

import com.insideout.backend.domain.tenant.dto.response.TenantSummaryResDTO;
import com.insideout.backend.domain.tenant.dto.request.TenantCreateReqDTO;
import com.insideout.backend.domain.tenant.entity.Tenant;
import com.insideout.backend.domain.tenant.entity.TenantMembership;
import com.insideout.backend.domain.tenant.repository.TenantMembershipRepository;
import com.insideout.backend.domain.tenant.repository.TenantRepository;
import com.insideout.backend.domain.user.entity.User;
import com.insideout.backend.domain.user.repository.UserRepository;
import com.insideout.backend.global.apiPayload.code.GeneralErrorCode;
import com.insideout.backend.global.apiPayload.exception.ProjectException;
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
    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;

    /**
     * 유저가 소속된 모든 테넌트 목록을 반환합니다.
     */
    public List<TenantSummaryResDTO> getMyTenants(UUID userId) {
        List<TenantMembership> memberships = tenantMembershipRepository.findAllByUser_Id(userId);

        return memberships.stream()
                .map(m -> TenantSummaryResDTO.from(m.getTenant(), m.getRole(), m.getJoinedAt()))
                .toList();
    }

    /**
     * 새로운 테넌트를 생성하고, 요청한 유저를 해당 테넌트의 관리자(owner)로 등록합니다.
     */
    @Transactional
    public TenantSummaryResDTO createTenant(UUID userId, TenantCreateReqDTO req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ProjectException(GeneralErrorCode.NOT_FOUND));
        String slug = req.slug() != null && !req.slug().isBlank()
                ? req.slug()
                : UUID.randomUUID().toString();
        if(tenantRepository.existsBySlug(slug)){
            throw new TenantException(TenantErrorCode.TENANT_SLUG_CONFLICT);
        }
        Tenant tenant = Tenant.builder()
                .displayName(req.name())
                .slug(slug)
                .status("approved") // 등록 즉시 활성화
                .build();
        Tenant savedTenant = tenantRepository.save(tenant);
        TenantMembership membership = TenantMembership.builder()
                .tenant(savedTenant)
                .user(user)
                .role("owner")
                .build();
        tenantMembershipRepository.save(membership);
        return TenantSummaryResDTO.from(savedTenant, membership.getRole(), membership.getJoinedAt());
    }

}

package com.insideout.backend.domain.tenant.service;

import com.insideout.backend.domain.building.entity.Building;
import com.insideout.backend.domain.building.repository.BuildingRepository;
import com.insideout.backend.domain.map.enums.MapType;
import com.insideout.backend.domain.map.repository.MapVersionRepository;
import com.insideout.backend.domain.tenant.dto.response.TenantSummaryResDTO;
import com.insideout.backend.domain.tenant.dto.request.TenantCreateReqDTO;
import com.insideout.backend.domain.tenant.entity.Tenant;
import com.insideout.backend.domain.tenant.entity.TenantMembership;
import com.insideout.backend.domain.tenant.exception.TenantErrorCode;
import com.insideout.backend.domain.tenant.exception.TenantException;
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
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TenantService {

    private final TenantMembershipRepository tenantMembershipRepository;
    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final BuildingRepository buildingRepository;
    private final MapVersionRepository mapVersionRepository;

    /**
     * 유저가 소속된 모든 테넌트 목록을 반환합니다.
     */
    public List<TenantSummaryResDTO> getMyTenants(UUID userId) {
        List<TenantMembership> memberships = tenantMembershipRepository.findAllByUser_Id(userId);
        if (memberships.isEmpty()) {
            return List.of();
        }

        List<UUID> tenantIds = memberships.stream()
                .map(m -> m.getTenant().getId())
                .toList();

        // 1. 모든 테넌트들의 빌딩을 한 번에 배치 조회
        List<Building> allBuildings = buildingRepository.findByTenant_IdIn(tenantIds);

        // 2. 테넌트 ID 별 빌딩 목록 그룹핑
        Map<UUID, List<Building>> buildingsByTenantId = allBuildings.stream()
                .collect(java.util.stream.Collectors.groupingBy(b -> b.getTenant().getId()));

        // 3. 모든 빌딩 ID 수집 후 한 번에 published 여부 조회
        List<UUID> buildingIds = allBuildings.stream()
                .map(Building::getId)
                .toList();

        Set<UUID> publishedBuildingIds = buildingIds.isEmpty()
                ? Set.of()
                : mapVersionRepository.findBuildingIdsByMapTypeAndStatus(buildingIds, MapType.BUILDING, "published");

        return memberships.stream()
                .map(membership -> {
                    UUID tenantId = membership.getTenant().getId();
                    List<Building> tenantBuildings = buildingsByTenantId.getOrDefault(tenantId, List.of());

                    // 테넌트에 속한 빌딩 중 published된 빌딩이 최소 하나라도 있는지 확인
                    boolean hasPublishedBuilding = tenantBuildings.stream()
                            .anyMatch(b -> publishedBuildingIds.contains(b.getId()));

                    boolean approvedByPublishedMap =
                            "approved".equals(membership.getTenant().getStatus()) || hasPublishedBuilding;

                    return TenantSummaryResDTO.from(
                            membership.getTenant(),
                            membership.getRole(),
                            membership.getJoinedAt(),
                            approvedByPublishedMap,
                            tenantBuildings.size()
                    );
                })
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
                .status("pending") // 최초 등록 시 대기 상태
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

    /**
     * 테넌트를 수동으로 활성화("approved")합니다.
     * 요청한 유저가 해당 테넌트의 소유자(owner)여야 합니다.
     */
    @Transactional
    public TenantSummaryResDTO activateTenant(UUID userId, UUID tenantId) {
        TenantMembership membership = tenantMembershipRepository.findByUser_IdAndTenant_Id(userId, tenantId)
                .orElseThrow(() -> new TenantException(TenantErrorCode.MEMBERSHIP_NOT_FOUND));

        if (!"owner".equals(membership.getRole())) {
            throw new TenantException(TenantErrorCode.NOT_TENANT_OWNER);
        }

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new TenantException(TenantErrorCode.TENANT_NOT_FOUND));

        tenant.updateStatus("approved");
        int buildingCount = buildingRepository.findByTenant_IdOrderByCreatedAtDesc(tenantId).size();
        return TenantSummaryResDTO.from(
                tenant,
                membership.getRole(),
                membership.getJoinedAt(),
                true,
                buildingCount
        );
    }

}

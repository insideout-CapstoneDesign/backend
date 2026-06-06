package com.insideout.backend.domain.tenant.dto.response;

import com.insideout.backend.domain.tenant.entity.Tenant;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 대시보드 테넌트 목록에서 사용하는 응답 DTO.
 */
public record TenantSummaryResDTO(
        UUID tenantId,
        String slug,
        String displayName,
        String status,
        int buildingCount,
        String role,
        OffsetDateTime joinedAt
) {
    public static TenantSummaryResDTO from(Tenant tenant, String role, OffsetDateTime joinedAt) {
        return from(tenant, role, joinedAt, false, 0);
    }

    public static TenantSummaryResDTO from(Tenant tenant, String role, OffsetDateTime joinedAt, boolean approvedByPublishedMap, int buildingCount) {
        return new TenantSummaryResDTO(
                tenant.getId(),
                tenant.getSlug(),
                tenant.getDisplayName(),
                approvedByPublishedMap ? "approved" : tenant.getStatus(),
                buildingCount,
                role,
                joinedAt
        );
    }
}

package com.insideout.backend.domain.tenant.dto;

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
        String role,
        OffsetDateTime joinedAt
) {
    public static TenantSummaryResDTO from(Tenant tenant, String role, OffsetDateTime joinedAt) {
        return new TenantSummaryResDTO(
                tenant.getId(),
                tenant.getSlug(),
                tenant.getDisplayName(),
                tenant.getStatus(),
                role,
                joinedAt
        );
    }
}

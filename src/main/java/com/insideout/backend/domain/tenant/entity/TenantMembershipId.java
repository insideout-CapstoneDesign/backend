package com.insideout.backend.domain.tenant.entity;

import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

/**
 * TenantMembership 엔티티의 복합키(Composite Key) 클래스.
 *
 * <p>tenant_id와 user_id를 묶어서 식별자로 사용합니다.
 */
@NoArgsConstructor
@EqualsAndHashCode
public class TenantMembershipId implements Serializable {
    private UUID tenant;
    private UUID user;
}

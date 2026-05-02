package com.insideout.backend.domain.tenant.entity;

import com.insideout.backend.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * 테넌트(건물/단지 그룹)와 사용자(건물 관리자)를 연결하는 다대다(N:M) 매핑 테이블.
 *
 * <p>현재 기획상 "1명의 관리자가 본인의 건물을 관리"하므로 
 * 복잡한 권한(role)보다는 "이 건물이 누구 소유인가?"를 식별하는 용도로 사용됩니다.
 */
@Entity
@Table(name = "tenant_membership")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@IdClass(TenantMembershipId.class)
public class TenantMembership {

    /**
     * 매핑되는 테넌트 (건물 또는 단지).
     * <p>복합키의 일부분으로 동작합니다.
     */
    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    /**
     * 매핑되는 사용자 (건물 관리자).
     * <p>복합키의 일부분으로 동작합니다.
     */
    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * 테넌트 내에서의 사용자 역할.
     *
     * <p>현재 요구사항에서는 권한 분리가 불필요하므로 무조건 "owner"로 고정하여 사용합니다.
     * (추후 공동 관리 기능 추가 시 활용될 수 있도록 필드만 유지합니다)
     */
    @Column(name = "role", nullable = false)
    private String role;

    /**
     * 건물을 등록하고 관리 권한을 얻은 시각.
     */
    @Column(name = "joined_at", nullable = false, updatable = false)
    private OffsetDateTime joinedAt;

    /**
     * 엔티티가 DB에 저장되기 전에 자동으로 실행되는 콜백.
     * <p>가입 시간을 기록하고, 역할(role)을 기본값인 "owner"로 세팅합니다.
     */
    @PrePersist
    void onPrePersist() {
        if (this.joinedAt == null) {
            this.joinedAt = OffsetDateTime.now();
        }
        if (this.role == null || this.role.isBlank()) {
            this.role = "owner";
        }
    }

    @Builder
    public TenantMembership(Tenant tenant, User user, String role) {
        this.tenant = tenant;
        this.user = user;
        // 빌더로 넘겼을 때도 비어있으면 owner로 고정
        this.role = (role != null && !role.isBlank()) ? role : "owner";
    }
}

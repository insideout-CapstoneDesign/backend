package com.insideout.backend.domain.building.entity;

import com.insideout.backend.domain.tenant.entity.Tenant;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.Polygon;
import jakarta.validation.constraints.Min;
import com.insideout.backend.domain.building.exception.BuildingErrorCode;
import com.insideout.backend.domain.building.exception.BuildingException;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 길찾기의 대상이 되는 개별 건물(Building) 엔티티.
 *
 * <p>하나의 테넌트(단지)는 여러 개의 건물을 가질 수 있습니다.
 * 건물이 1개인 경우에도 플랫폼 등록 시 단지-건물 계층 구조를 따릅니다.
 */
@Entity
@Table(name = "building")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Building {

    /**
     * 건물의 고유 식별자 (PK).
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * 이 건물이 속한 테넌트 (단지/기관).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    /**
     * 이 건물이 속한 캠퍼스/대형 단지.
     * <p>null이면 단독 건물로 취급합니다.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campus_id")
    private Campus campus;

    /**
     * 건물명.
     * <p>예시: "제1공학관", "본관".
     */
    @Column(nullable = false)
    private String name;

    /**
     * 건물의 도로명 또는 지번 주소.
     */
    private String address;

    /**
     * 건물의 2D 지표면 경계(외곽선) 정보.
     * <p>실외 지도(카카오 등) 위에 오버레이를 띄울 때 건물이 차지하는 영역을 나타냅니다.
     * PostGIS의 geography(Polygon, 4326)에 매핑됩니다.
     */
    @Column(columnDefinition = "geography(Polygon, 4326)")
    private Polygon footprint;

    /**
     * 건물 내부에 접근할 수 있는 주출입구/부출입구의 개수.
     */
    @Min(0)
    @Column(name = "entrance_count", nullable = false)
    private int entranceCount;

    /**
     * 건물의 추가적인 메타데이터.
     * <p>운영 시간, 전화번호 등 정형화하기 어려운 정보들을 JSON 형태로 유연하게 저장합니다.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> meta;

    /**
     * 외부 지도 API(카카오맵, 네이버맵 등)에서의 식별자.
     * <p>실외 길찾기와 연동 시 매핑을 위해 사용됩니다.
     */
    @Column(name = "external_api_id")
    private String externalApiId;

    /**
     * 건물이 시스템에 등록된 시각.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onPrePersist() {
        if (this.createdAt == null) {
            this.createdAt = OffsetDateTime.now();
        }
        if (this.meta == null) {
            this.meta = Map.of();
        }
    }

    @Builder
    public Building(Tenant tenant, Campus campus, String name, String address, Polygon footprint, int entranceCount, Map<String, Object> meta, String externalApiId) {
        if (entranceCount < 0) {
            throw new BuildingException(BuildingErrorCode.NEGATIVE_ENTRANCE_COUNT);
        }
        if (hasDifferentTenant(tenant, campus)) {
            throw new BuildingException(BuildingErrorCode.BUILDING_CAMPUS_TENANT_MISMATCH);
        }
        this.tenant = tenant;
        this.campus = campus;
        this.name = name;
        this.address = address;
        this.footprint = footprint;
        this.entranceCount = entranceCount;
        this.meta = meta != null ? meta : Map.of();
        this.externalApiId = externalApiId;
    }

    public boolean hasCampus() {
        return this.campus != null;
    }

    public void updateEntranceCount(int entranceCount) {
        if (entranceCount < 0) {
            throw new BuildingException(BuildingErrorCode.NEGATIVE_ENTRANCE_COUNT);
        }
        this.entranceCount = entranceCount;
    }

    private boolean hasDifferentTenant(Tenant tenant, Campus campus) {
        if (tenant == null || campus == null || campus.getTenant() == null) {
            return false;
        }

        UUID tenantId = tenant.getId();
        UUID campusTenantId = campus.getTenant().getId();
        if (tenantId != null || campusTenantId != null) {
            return !Objects.equals(tenantId, campusTenantId);
        }

        return tenant != campus.getTenant();
    }
}

package com.insideout.backend.domain.building.entity;

import com.insideout.backend.domain.building.exception.BuildingErrorCode;
import com.insideout.backend.domain.building.exception.BuildingException;
import com.insideout.backend.domain.map.entity.MapVersion;
import com.insideout.backend.domain.tenant.entity.Tenant;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * 앱 전역 검색 및 메인 화면 리스트업을 위한 건물 통합 인덱스 테이블.
 *
 * <p>모든 Building 정보 중 대중에게 공개(public)되고 배포된(published) 데이터만
 * 모아두어, 검색 성능을 극대화하기 위해 사용됩니다. (요구사항 5, 6, 7번)
 */
@Entity
@Table(name = "building_directory")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BuildingDirectory {

    /**
     * Building 엔티티의 ID와 동일한 값을 사용합니다. (1:1 관계의 읽기 전용 뷰 느낌)
     */
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campus_id")
    private Campus campus;

    @Column(nullable = false)
    private String name;

    private String address;

    /**
     * 건물 분류 카테고리 (대학, 병원, 쇼핑몰 등).
     */
    private String category;

    /**
     * 건물의 중심점 좌표(GPS). 검색 시 거리순 정렬 등에 사용됩니다.
     */
    @Column(columnDefinition = "geography(Point, 4326)")
    private Point centroid;

    /**
     * 건물이 차지하는 영역(GPS).
     */
    @Column(columnDefinition = "geography(Polygon, 4326)")
    private Polygon bbox;

    /**
     * 일반 사용자에게 공개할지 여부.
     */
    @Column(name = "is_public", nullable = false)
    private boolean isPublic;

    /**
     * 이 건물이 현재 제공하고 있는 실제 도면/길찾기 데이터의 버전.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "published_version_id")
    private MapVersion publishedVersion;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }

    @Builder
    public BuildingDirectory(UUID id, Tenant tenant, Campus campus, String name, String address, String category, Point centroid, Polygon bbox, boolean isPublic, MapVersion publishedVersion) {
        if (hasDifferentTenant(tenant, campus)) {
            throw new BuildingException(BuildingErrorCode.BUILDING_DIRECTORY_CAMPUS_TENANT_MISMATCH);
        }
        this.id = id;
        this.tenant = tenant;
        this.campus = campus;
        this.name = name;
        this.address = address;
        this.category = category;
        this.centroid = centroid;
        this.bbox = bbox;
        this.isPublic = isPublic;
        this.publishedVersion = publishedVersion;
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

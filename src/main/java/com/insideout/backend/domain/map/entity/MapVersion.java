package com.insideout.backend.domain.map.entity;

import com.insideout.backend.domain.building.entity.Campus;
import com.insideout.backend.domain.building.entity.Building;
import com.insideout.backend.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 길찾기 그래프(노드, 엣지, POI 등)의 버전을 관리하는 엔티티.
 *
 * <p>관리자가 맵 에디터에서 작업을 하더라도 사용자에게 바로 노출되지 않고,
 * 'published' 상태의 버전만 서비스에 반영됩니다.
 * 작업 히스토리 추적 및 롤백이 가능하도록 설계되었습니다.
 */
@Entity
@Table(name = "map_version")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MapVersion {

    /**
     * 맵 버전의 고유 식별자 (PK).
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * 이 맵 버전이 속한 테넌트 식별자.
     */
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "map_type", nullable = false)
    private MapType mapType;

    /**
     * 캠퍼스 그래프 버전일 때 채워집니다.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campus_id")
    private Campus campus;

    /**
     * 이 맵 버전이 속한 건물.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "building_id")
    private Building building;

    /**
     * 버전을 구별하기 위한 라벨.
     * <p>예시: "2024-1학기 개편 반영", "v1.0.0".
     */
    @Column(nullable = false)
    private String label;

    /**
     * 버전의 상태.
     * <p>'draft'(임시저장), 'published'(배포됨), 'archived'(과거 버전).
     */
    @Column(nullable = false)
    private String status;

    /**
     * 이 버전이 파생된 부모 버전의 식별자. (히스토리 추적용)
     */
    @Column(name = "parent_version_id")
    private UUID parentVersionId;

    /**
     * 이 버전을 생성한 관리자.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    /**
     * 버전이 생성된(초안이 만들어진) 시각.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    /**
     * 이 버전이 사용자들에게 배포된 시각 ('published' 상태 전환 시각).
     */
    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    @PrePersist
    void onPrePersist() {
        if (this.createdAt == null) {
            this.createdAt = OffsetDateTime.now();
        }
        if (this.status == null) {
            this.status = "draft";
        }
        if (this.mapType == null) {
            this.mapType = MapType.BUILDING;
        }
    }

    @Builder
    public MapVersion(UUID tenantId, MapType mapType, Campus campus, Building building, String label, String status, UUID parentVersionId, User createdBy, OffsetDateTime publishedAt) {
        String finalStatus = status != null ? status : "draft";
        OffsetDateTime finalPublishedAt = publishedAt;

        if ("published".equals(finalStatus) && finalPublishedAt == null) {
            finalPublishedAt = OffsetDateTime.now();
        } else if (finalPublishedAt != null && !"published".equals(finalStatus)) {
            finalStatus = "published";
        }

        this.tenantId = tenantId;
        this.mapType = mapType != null ? mapType : inferMapType(campus);
        this.campus = campus;
        this.building = building;
        this.label = label;
        this.status = finalStatus;
        this.parentVersionId = parentVersionId;
        this.createdBy = createdBy;
        this.publishedAt = finalPublishedAt;
    }

    private MapType inferMapType(Campus campus) {
        return campus != null ? MapType.CAMPUS : MapType.BUILDING;
    }
}

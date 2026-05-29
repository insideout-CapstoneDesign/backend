package com.insideout.backend.domain.map.entity;

import com.insideout.backend.domain.building.entity.Floor;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.Polygon;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 도면 상의 특정 영역(Zone)을 나타내는 엔티티.
 *
 * <p>단순 점(Point)이 아닌 면(Polygon) 데이터이며,
 * '복도', '일반 방', '접근 금지 구역' 등을 색칠하거나 
 * 영역별 길찾기 회피 구역 등을 설정할 때 사용합니다.
 */
@Entity
@Table(name = "zone")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Zone {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "map_version_id", nullable = false)
    private MapVersion mapVersion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "floor_id", nullable = false)
    private Floor floor;

    /**
     * 영역의 종류.
     * <p>허용값은 {@link ZoneKind} 참고. DB의 CHECK 제약과 동기화되어 있습니다.
     */
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private ZoneKind kind;

    /**
     * 영역의 명칭 (옵션).
     * <p>예: "A동 1층 로비 영역", "통제구역 1"
     */
    private String name;

    /**
     * 도면 상의 다각형 영역 (Pixel 단위).
     * <p>렌더링 엔진(프론트엔드)에서 지도에 색상 오버레이를 그릴 때 사용됩니다.
     */
    @Column(name = "geom_px", columnDefinition = "geometry(Polygon, 0)", nullable = false)
    private Polygon geomPx;

    /**
     * 영역에 대한 추가 속성 데이터 (디스플레이 컬러 등).
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> properties;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onPrePersist() {
        if (this.createdAt == null) {
            this.createdAt = OffsetDateTime.now();
        }
        if (this.properties == null) {
            this.properties = Map.of();
        }
    }

    @Builder
    public Zone(UUID tenantId, MapVersion mapVersion, Floor floor, ZoneKind kind, String name, Polygon geomPx, Map<String, Object> properties) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.mapVersion = Objects.requireNonNull(mapVersion, "mapVersion must not be null");
        this.floor = Objects.requireNonNull(floor, "floor must not be null");
        this.kind = Objects.requireNonNull(kind, "kind must not be null");
        this.name = name;
        this.geomPx = Objects.requireNonNull(geomPx, "geomPx must not be null");
        this.properties = properties != null ? properties : Map.of();
    }
}

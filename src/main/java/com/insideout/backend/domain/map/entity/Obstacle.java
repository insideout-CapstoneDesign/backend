package com.insideout.backend.domain.map.entity;

import com.insideout.backend.domain.building.entity.Building;
import com.insideout.backend.domain.building.entity.Campus;
import com.insideout.backend.domain.building.entity.Floor;
import com.insideout.backend.domain.map.exception.MapErrorCode;
import com.insideout.backend.domain.map.exception.MapException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.Geometry;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 길찾기 중 일시적이거나 동적인 통행 제한 구역/장애물을 나타내는 엔티티.
 *
 * <p>공사 중, 청소 중, 행사 등의 이유로 기존의 엣지(Edge)를 막거나
 * 비용(가중치)을 증가시켜야 할 때 사용됩니다.
 * 영구적인 맵 버전을 업데이트하지 않고도 실시간으로 경로를 우회시킬 수 있습니다.
 */
@Entity
@Table(name = "obstacle")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Obstacle {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "building_id")
    private Building building;

    /**
     * 캠퍼스/단지 그래프에 적용되는 장애물.
     * <p>건물 내부 장애물은 building에, 캠퍼스 내부 장애물은 campus에 연결합니다.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campus_id")
    private Campus campus;

    /**
     * 장애물이 위치한 층.
     * <p>null일 경우 건물 전체에 영향을 미치는 것으로 해석할 수 있습니다.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "floor_id")
    private Floor floor;

    /**
     * 장애물의 종류. (construction, closed, slippery, event 등)
     */
    @Column(nullable = false)
    private String kind;

    /**
     * 장애물이 차지하는 물리적 공간 (도면 픽셀 단위).
     * <p>프론트엔드에 장애물 경고 아이콘이나 위험 구역(Polygon)을 렌더링하기 위해 사용됩니다.
     */
    @Column(name = "geom_px", columnDefinition = "geometry")
    private Geometry geomPx;

    /**
     * 이 장애물로 인해 직접적으로 영향을 받는 길찾기 엣지(Edge)들의 ID 리스트.
     * <p>이 배열에 속한 엣지는 라우팅 시 페널티를 받거나 차단됩니다.
     */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "affected_edge_ids", columnDefinition = "uuid[]")
    private List<UUID> affectedEdgeIds;

    /**
     * 이 구역을 지나갈 때 부여되는 추가적인 거리 가중치(페널티).
     * <p>미끄러움 등으로 인해 "되도록 피하되 불가피하면 지나가는 길"로 만들고 싶을 때 사용.
     */
    @Column(name = "extra_cost", nullable = false, precision = 10, scale = 2)
    private BigDecimal extraCost;

    /**
     * 이 구역이 완전히 차단되어 통행이 불가능한지 여부.
     * <p>true이면 라우팅 엔진은 해당 affected_edge_ids를 경로에서 완전 제외합니다.
     */
    @Column(name = "is_blocking", nullable = false)
    private boolean isBlocking;

    /**
     * 장애물의 유효 시작 시간. (예: 공사 시작일)
     */
    @Column(name = "active_from")
    private OffsetDateTime activeFrom;

    /**
     * 장애물의 유효 종료 시간. (예: 공사 종료일)
     * <p>이 시간이 지나면 라우팅 엔진이 장애물을 무시합니다.
     */
    @Column(name = "active_to")
    private OffsetDateTime activeTo;

    /**
     * 사용자에게 노출될 장애물 관련 안내 문구.
     * <p>예: "바닥 왁스 작업 중이므로 우회 바랍니다."
     */
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onPrePersist() {
        if (this.createdAt == null) {
            this.createdAt = OffsetDateTime.now();
        }
        if (this.extraCost == null) {
            this.extraCost = BigDecimal.ZERO;
        }
    }

    // TODO: Obstacle 생성 시 아래 불변식을 Service에서 반드시 검증해야 합니다.
    //       building.getTenant() 및 floor.getBuilding()은 LAZY 로딩 연관 엔티티 호출이므로
    //       생성자 안에서 호출 시 LazyInitializationException이 발생합니다.
    //       ObstacleService에서 building, floor를 완전히 로딩한 후 검증하세요:
    //
    //       1. tenantId가 building의 tenant와 일치하는지:
    //          if (!tenantId.equals(building.getTenant().getId()))
    //              throw new MapException(MapErrorCode.OBSTACLE_TENANT_MISMATCH);
    //
    //       2. floor가 building 소속인지:
    //          if (!floor.getBuilding().getId().equals(building.getId()))
    //              throw new MapException(MapErrorCode.OBSTACLE_BUILDING_MISMATCH);
    @Builder
    public Obstacle(UUID tenantId, Building building, Campus campus, Floor floor, String kind, Geometry geomPx, List<UUID> affectedEdgeIds, BigDecimal extraCost, boolean isBlocking, OffsetDateTime activeFrom, OffsetDateTime activeTo, String note) {
        BigDecimal finalExtraCost = extraCost != null ? extraCost : BigDecimal.ZERO;

        // 필수 필드 null 선검증 (DB nullable=false 콜럼과 동기화)
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(kind, "kind must not be null");

        if ((building == null && campus == null) || (building != null && campus != null)) {
            throw new MapException(MapErrorCode.OBSTACLE_BUILDING_MISMATCH);
        }
        if (floor != null && building == null) {
            throw new MapException(MapErrorCode.OBSTACLE_BUILDING_MISMATCH);
        }

        if (finalExtraCost.signum() < 0) {
            throw new MapException(MapErrorCode.NEGATIVE_EXTRA_COST);
        }
        if (activeFrom != null && activeTo != null && activeFrom.isAfter(activeTo)) {
            throw new MapException(MapErrorCode.INVALID_ACTIVE_PERIOD);
        }
        // floor.getTenantId()는 UUID 직접 필드이므로 생성자에서 안전하게 검증 가능
        if (floor != null && floor.getTenantId() != null
                && !tenantId.equals(floor.getTenantId())) {
            throw new MapException(MapErrorCode.OBSTACLE_TENANT_MISMATCH);
        }

        this.tenantId = tenantId;
        this.building = building;
        this.campus = campus;
        this.floor = floor;
        this.kind = kind;
        this.geomPx = geomPx;
        this.affectedEdgeIds = affectedEdgeIds;
        this.extraCost = finalExtraCost;
        this.isBlocking = isBlocking;
        this.activeFrom = activeFrom;
        this.activeTo = activeTo;
        this.note = note;
    }
}

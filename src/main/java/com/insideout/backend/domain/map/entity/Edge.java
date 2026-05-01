package com.insideout.backend.domain.map.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.LineString;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * 두 노드(Node)를 연결하는 경로인 엣지(Edge) 엔티티.
 *
 * <p>길찾기 알고리즘의 간선(Edge) 역할을 수행하며,
 * 거리, 가중치(오르막길, 계단 등) 정보를 가집니다.
 */
@Entity
@Table(name = "edge")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Edge {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "map_version_id", nullable = false)
    private MapVersion mapVersion;

    /**
     * 출발 노드.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_node_id", nullable = false)
    private Node fromNode;

    /**
     * 도착 노드.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_node_id", nullable = false)
    private Node toNode;

    /**
     * 엣지의 종류 (보행로, 계단, 엘리베이터, 문 등).
     * <p>EdgeKind 테이블의 코드를 저장합니다. 가중치 계산의 핵심이 됩니다.
     */
    @Column(name = "kind", nullable = false)
    private String kindCode;

    /**
     * 엣지의 도면 픽셀상 형태 (선 렌더링 시 필요).
     * <p>노드간 직선이 아닌 꺾인 선형일 수 있으므로 LineString으로 저장합니다.
     */
    @Column(name = "geom_px", columnDefinition = "geometry(LineString, 0)")
    private LineString geomPx;

    /**
     * 엣지의 실제 지구 상의 선형 (위경도 및 고도).
     */
    @Column(name = "geom_wgs84", columnDefinition = "geography(LineStringZ, 4326)")
    private LineString geomWgs84;

    /**
     * 노드 간의 실제 물리적 거리 (미터).
     * <p>길찾기 최단거리 계산 시 사용됩니다.
     */
    @Column(name = "length_m", precision = 10, scale = 2)
    private BigDecimal lengthM;

    /**
     * 방향성 여부.
     * <p>true이면 fromNode -> toNode 로만 이동 가능합니다. (예: 에스컬레이터 상행)
     */
    @Column(name = "is_directed", nullable = false)
    private boolean isDirected;

    /**
     * 엣지 고유의 가중치 배수.
     * <p>기본값은 1.0이며, 이 엣지가 특별히 건너기 힘들거나 편하면 값을 조절합니다.
     */
    @Column(name = "base_weight", nullable = false, precision = 10, scale = 2)
    private BigDecimal baseWeight;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> properties;

    @Column(nullable = false)
    private String source;

    @Column(name = "ai_detection_id")
    private UUID aiDetectionId;

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
        if (this.baseWeight == null) {
            this.baseWeight = BigDecimal.ONE;
        }
        if (this.source == null) {
            this.source = "manual";
        }
    }

    @Builder
    public Edge(UUID tenantId, MapVersion mapVersion, Node fromNode, Node toNode, String kindCode, LineString geomPx, LineString geomWgs84, BigDecimal lengthM, boolean isDirected, BigDecimal baseWeight, Map<String, Object> properties, String source, UUID aiDetectionId) {
        this.tenantId = tenantId;
        this.mapVersion = mapVersion;
        this.fromNode = fromNode;
        this.toNode = toNode;
        this.kindCode = kindCode;
        this.geomPx = geomPx;
        this.geomWgs84 = geomWgs84;
        this.lengthM = lengthM;
        this.isDirected = isDirected;
        this.baseWeight = baseWeight != null ? baseWeight : BigDecimal.ONE;
        this.properties = properties != null ? properties : Map.of();
        this.source = source != null ? source : "manual";
        this.aiDetectionId = aiDetectionId;
    }
}

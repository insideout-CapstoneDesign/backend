package com.insideout.backend.domain.map.entity;

import com.insideout.backend.domain.building.entity.Floor;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.Point;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * 실내 길찾기 경로의 교차점, 출입구, 이동 지점을 나타내는 노드(Node) 엔티티.
 *
 * <p>맵 에디터에서 사용자가 지정하거나 AI가 추출한 교차점이며,
 * A* 알고리즘 등에서 정점(Vertex) 역할을 합니다.
 */
@Entity
@Table(name = "node")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Node {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    /**
     * 노드가 속한 맵 버전.
     * <p>버전이 다르면 노드 그래프도 완전히 다를 수 있습니다.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "map_version_id", nullable = false)
    private MapVersion mapVersion;

    /**
     * 이 노드가 위치한 층.
     * <p>건물 내부 그래프에서는 필수이며, 캠퍼스 그래프에서는 null일 수 있습니다.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "floor_id")
    private Floor floor;

    /**
     * 노드의 종류. (복도 교차점, 엘리베이터, 계단 등)
     * <p>NodeKind 테이블의 코드를 참조합니다. (수동 관리를 위해 다대일 연관관계 대신 코드 문자열 직접 저장)
     */
    @Column(name = "kind", nullable = false)
    private String kindCode;

    /**
     * 도면 이미지 상의 2D 좌표(Pixel).
     * <p>맵 에디터에 노드를 렌더링하거나, 픽셀 기준 길찾기 선을 그릴 때 사용합니다.
     */
    @Column(name = "geom_px", columnDefinition = "geometry(Point, 0)", nullable = false)
    private Point geomPx;

    /**
     * 실제 지구 상의 3D 좌표 (위도, 경도, 고도).
     * <p>실외 지도(카카오/네이버 맵)와 매핑될 때 사용됩니다.
     */
    @Column(name = "geom_wgs84", columnDefinition = "geography(PointZ, 4326)")
    private Point geomWgs84;

    /**
     * 노드의 명칭. (옵션)
     * <p>예: "서문 출입구", "A동 연결통로"
     */
    @Column(name = "name_ko")
    private String nameKo;

    /**
     * 노드의 추가 속성들을 담는 JSON 데이터.
     * <p>휠체어 접근 가능 여부 등 확장 데이터를 유연하게 담습니다.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> properties;

    /**
     * 노드가 어떻게 생성되었는지 출처.
     * <p>'ai'(자동추출), 'ai_confirmed'(관리자 확정), 'manual'(관리자 직접 생성).
     */
    @Column(nullable = false)
    private String source;

    /**
     * AI가 추출한 노드인 경우, 원본 AI 탐지 결과(AiDetection)의 ID.
     */
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
        if (this.source == null) {
            this.source = "manual";
        }
    }

    // TODO: Node 생성 시 mapVersion과 floor가 동일한 Building을 참조하는지 반드시 검증해야 합니다.
    //       LAZY 로딩 특성상 이 생성자 안에서 검증하면 LazyInitializationException이 발생할 수 있으므로,
    //       NodeService에서 두 엔티티를 완전히 로딩한 후 아래와 같이 검증하고 생성하세요:
    //
    //       if (!floor.getBuilding().getId().equals(mapVersion.getBuilding().getId())) {
    //           throw new MapException(MapErrorCode.BUILDING_MISMATCH);
    //       }

//    // NodeService 또는 NodeFactory 내부 (트랜잭션 컨텍스트 안)
//    public Node createNode(CreateNodeRequest request) {
//        MapVersion mapVersion = mapVersionRepository.findById(request.mapVersionId())
//                .orElseThrow(...);
//        Floor floor = floorRepository.findById(request.floorId())
//                .orElseThrow(...);
//
//        // Building 일관성 검증 — 두 엔티티가 이미 로드된 상태이므로 안전
//        if (!floor.getBuilding().getId().equals(mapVersion.getBuilding().getId())) {
//            throw new MapException(MapErrorCode.BUILDING_MISMATCH);
//        }
//
//        return nodeRepository.save(Node.builder()
//                .mapVersion(mapVersion)
//                .floor(floor)
//        ...
//        .build());
//    }
    @Builder
    public Node(UUID tenantId, MapVersion mapVersion, Floor floor, String kindCode, Point geomPx, Point geomWgs84, String nameKo, Map<String, Object> properties, String source, UUID aiDetectionId) {
        this.tenantId = tenantId;
        this.mapVersion = mapVersion;
        this.floor = floor;
        this.kindCode = kindCode;
        this.geomPx = geomPx;
        this.geomWgs84 = geomWgs84;
        this.nameKo = nameKo;
        this.properties = properties != null ? properties : Map.of();
        this.source = source != null ? source : "manual";
        this.aiDetectionId = aiDetectionId;
    }
}

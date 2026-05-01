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
import org.locationtech.jts.geom.Polygon;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 길찾기 및 통합 검색의 대상이 되는 주요 관심 지점(Point of Interest) 엔티티.
 *
 * <p>식당, 화장실, 엘리베이터, 매장 등 사용자가 최종 목적지로 선택하거나
 * 도면 상에 마커로 표시되는 지점들을 의미합니다. (요구사항 10번, 12번)
 */
@Entity
@Table(name = "poi")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Poi {

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
     * POI의 분류 카테고리 (화장실, 식당 등).
     * <p>검색 필터링이나 지도 상의 아이콘(마커) 이미지를 결정하는 데 사용됩니다.
     */
    @Column(name = "category_id")
    private Long categoryId;

    /**
     * POI의 명칭 (자동완성 및 검색어 매칭 대상).
     * <p>예시: "스타벅스", "남자화장실".
     */
    @Column(nullable = false)
    private String name;

    /**
     * POI 식별용 코드 (보통 학사/사내 시스템 연동 시 호실 번호 등).
     */
    private String code;

    /**
     * 도면 이미지 상의 중심점 좌표(Pixel).
     * <p>사용자가 도면을 볼 때 POI 마커가 렌더링될 위치입니다.
     */
    @Column(name = "geom_px", columnDefinition = "geometry(Point, 0)", nullable = false)
    private Point geomPx;

    /**
     * 도면 이미지 상에서 이 POI가 차지하는 실제 면적/경계 다각형(Pixel).
     * <p>지도에서 해당 영역 터치 시 이 POI를 선택하게 만들 때 사용됩니다.
     */
    @Column(name = "footprint_px", columnDefinition = "geometry(Polygon, 0)")
    private Polygon footprintPx;

    /**
     * 실제 위경도 좌표. (실외 지도와 통합 렌더링 시 필요)
     */
    @Column(name = "geom_wgs84", columnDefinition = "geography(Point, 4326)")
    private Point geomWgs84;

    /**
     * 목적지로 설정되었을 때 길찾기 안내가 끝나는 실제 도착지 노드(Node) ID.
     * <p>매장 중심(POI)까지 들어가는게 아니라 "매장 입구 노드"까지 안내하기 위해 사용됩니다.
     */
    @Column(name = "anchor_node_id")
    private UUID anchorNodeId;

    /**
     * 검색 키워드 확장을 위한 태그 목록.
     * <p>예: ["커피", "디저트", "조용한"] -> "커피" 검색 시 이 POI 노출.
     */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]")
    private List<String> tags;

    /**
     * POI의 운영시간, 전화번호, 사진URL 등 부가 정보 (요구사항 8번 연동).
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> attrs;

    /**
     * 네이버/카카오/구글 플레이스 API 등 외부 연동 ID.
     */
    @Column(name = "external_api_id")
    private String externalApiId;

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
        if (this.attrs == null) {
            this.attrs = Map.of();
        }
        if (this.source == null) {
            this.source = "manual";
        }
    }

    @Builder
    public Poi(UUID tenantId, MapVersion mapVersion, Floor floor, Long categoryId, String name, String code, Point geomPx, Polygon footprintPx, Point geomWgs84, UUID anchorNodeId, List<String> tags, Map<String, Object> attrs, String externalApiId, String source, UUID aiDetectionId) {
        this.tenantId = tenantId;
        this.mapVersion = mapVersion;
        this.floor = floor;
        this.categoryId = categoryId;
        this.name = name;
        this.code = code;
        this.geomPx = geomPx;
        this.footprintPx = footprintPx;
        this.geomWgs84 = geomWgs84;
        this.anchorNodeId = anchorNodeId;
        this.tags = tags;
        this.attrs = attrs != null ? attrs : Map.of();
        this.externalApiId = externalApiId;
        this.source = source != null ? source : "manual";
        this.aiDetectionId = aiDetectionId;
    }
}

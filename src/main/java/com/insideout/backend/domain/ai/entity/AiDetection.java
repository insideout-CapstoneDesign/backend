package com.insideout.backend.domain.ai.entity;

import com.insideout.backend.domain.building.entity.Floorplan;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.Geometry;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * AI가 도면에서 탐지해낸 개별 객체(문, 복도, 화장실, 글자 등)의 결과물.
 *
 * <p>AI가 분석을 마치면 이 테이블에 결과들이 쌓이고,
 * 건물 관리자는 맵 에디터에서 이 결과들을 보고 승인(accepted)하거나 거절(rejected)합니다.
 * 승인된 탐지물은 실제 Node, Edge, Poi 엔티티로 변환됩니다.
 */
@Entity
@Table(name = "ai_detection")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiDetection {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id", nullable = false)
    private AiJob job;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "floorplan_id", nullable = false)
    private Floorplan floorplan;

    /**
     * AI가 식별한 객체의 종류.
     * <p>'wall', 'door', 'room', 'elevator', 'text', 'poi_candidate' 등.
     */
    @Column(name = "detect_type", nullable = false)
    private String detectType;

    /**
     * AI가 인식한 라벨명.
     */
    private String label;

    /**
     * AI의 예측 확신도 (0.0 ~ 1.0).
     * <p>수치가 낮으면 맵 에디터에서 "주의해서 검토하세요"라고 표시할 수 있습니다.
     */
    @Column(precision = 5, scale = 4)
    private BigDecimal confidence;

    /**
     * 객체의 도면 상 기하학적 모양 (Point, Polygon 등 자유로운 형태).
     */
    @Column(name = "geom_px", columnDefinition = "geometry(Geometry, 0)", nullable = false)
    private Geometry geomPx;

    /**
     * 객체를 감싸는 직사각형 바운딩 박스.
     */
    @Column(name = "bbox_px", columnDefinition = "box2d")
    @org.hibernate.annotations.ColumnTransformer(read = "bbox_px::text", write = "?::box2d")
    private String bboxPx; // box2d는 JPA에서 String 매핑이 간편합니다.

    /**
     * 텍스트 인식(OCR) 결과일 경우 읽어낸 글자.
     */
    @Column(name = "ocr_text")
    private String ocrText;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> attrs;

    /**
     * 관리자 검토 상태.
     * <p>'pending'(대기중), 'accepted'(승인됨), 'rejected'(기각됨).
     */
    @Column(nullable = false)
    private String status;

    /**
     * 관리자 승인으로 인해 실제로 생성된 시스템 엔티티의 종류 ('node', 'edge', 'poi' 등).
     */
    @Column(name = "committed_entity_type")
    private String committedEntityType;

    /**
     * 생성된 시스템 엔티티의 실제 ID.
     */
    @Column(name = "committed_entity_id")
    private UUID committedEntityId;

    @PrePersist
    void onPrePersist() {
        if (this.status == null) {
            this.status = "pending";
        }
    }

    @Builder
    public AiDetection(UUID tenantId, AiJob job, Floorplan floorplan, String detectType, String label, BigDecimal confidence, Geometry geomPx, String bboxPx, String ocrText, Map<String, Object> attrs, String status, String committedEntityType, UUID committedEntityId) {
        this.tenantId = tenantId;
        this.job = job;
        this.floorplan = floorplan;
        this.detectType = detectType;
        this.label = label;
        this.confidence = confidence;
        this.geomPx = geomPx;
        this.bboxPx = bboxPx;
        this.ocrText = ocrText;
        this.attrs = attrs;
        this.status = status != null ? status : "pending";
        this.committedEntityType = committedEntityType;
        this.committedEntityId = committedEntityId;
    }
}

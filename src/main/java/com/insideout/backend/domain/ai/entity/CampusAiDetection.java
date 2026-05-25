package com.insideout.backend.domain.ai.entity;

import com.insideout.backend.domain.building.entity.CampusMap;
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
 * AI가 캠퍼스 도면에서 탐지해낸 개별 객체(정문, 도로, 건물 영역, 장애물 등)의 결과물.
 */
@Entity
@Table(name = "campus_ai_detection")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CampusAiDetection {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id", nullable = false)
    private CampusAiJob job;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campus_map_id", nullable = false)
    private CampusMap campusMap;

    @Column(name = "detect_type", nullable = false)
    private String detectType;

    private String label;

    @Column(precision = 5, scale = 4)
    private BigDecimal confidence;

    @Column(name = "geom_px", columnDefinition = "geometry(Geometry, 0)", nullable = false)
    private Geometry geomPx;

    @Column(name = "bbox_px", columnDefinition = "box2d")
    @org.hibernate.annotations.ColumnTransformer(read = "bbox_px::text", write = "?::box2d")
    private String bboxPx;

    @Column(name = "ocr_text")
    private String ocrText;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> attrs;

    @Column(nullable = false)
    private String status;

    @Column(name = "committed_entity_type")
    private String committedEntityType;

    @Column(name = "committed_entity_id")
    private UUID committedEntityId;

    @PrePersist
    void onPrePersist() {
        if (this.status == null) {
            this.status = "pending";
        }
    }

    @Builder
    public CampusAiDetection(UUID tenantId, CampusAiJob job, CampusMap campusMap, String detectType, String label, BigDecimal confidence, Geometry geomPx, String bboxPx, String ocrText, Map<String, Object> attrs, String status, String committedEntityType, UUID committedEntityId) {
        this.tenantId = tenantId;
        this.job = job;
        this.campusMap = campusMap;
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

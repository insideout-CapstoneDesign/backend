package com.insideout.backend.domain.ai.entity;

import com.insideout.backend.domain.building.entity.CampusMap;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * 캠퍼스 야외 도면 AI 파이프라인의 작업 내역을 관리하는 엔티티.
 */
@Entity
@Table(name = "campus_ai_job")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CampusAiJob {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campus_map_id", nullable = false)
    private CampusMap campusMap;

    @Column(nullable = false)
    private String status;

    @Column(name = "model_version")
    private String modelVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> params;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "finished_at")
    private OffsetDateTime finishedAt;

    private String error;

    @PrePersist
    void onPrePersist() {
        if (this.status == null) {
            this.status = "queued";
        }
        if (this.createdAt == null) {
            this.createdAt = OffsetDateTime.now();
        }
    }

    @Builder
    public CampusAiJob(UUID tenantId, CampusMap campusMap, String status, String modelVersion, Map<String, Object> params, OffsetDateTime createdAt, OffsetDateTime startedAt, OffsetDateTime finishedAt, String error) {
        if (tenantId != null && campusMap != null && campusMap.getTenantId() != null
                && !tenantId.equals(campusMap.getTenantId())) {
            throw new IllegalArgumentException("CampusAiJob tenantId must match campusMap.tenantId");
        }
        this.tenantId = tenantId;
        this.campusMap = campusMap;
        this.status = status != null ? status : "queued";
        this.modelVersion = modelVersion;
        this.params = params;
        this.createdAt = createdAt != null ? createdAt : OffsetDateTime.now();
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
        this.error = error;
    }

    public void markRunning() {
        this.status = "running";
        this.startedAt = OffsetDateTime.now();
        this.finishedAt = null;
        this.error = null;
    }

    public void markSucceeded() {
        this.status = "succeeded";
        this.finishedAt = OffsetDateTime.now();
        this.error = null;
    }

    public void markFailed(String error) {
        this.status = "failed";
        this.finishedAt = OffsetDateTime.now();
        this.error = error;
    }
}

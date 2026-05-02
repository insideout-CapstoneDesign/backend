package com.insideout.backend.domain.ai.entity;

import com.insideout.backend.domain.building.entity.Floorplan;
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
 * AI 파이프라인(객체 추출, 텍스트 인식, POI 추출 등)의 작업 내역을 관리하는 엔티티.
 *
 * <p>도면 이미지를 업로드한 후 비동기로 실행되는 머신러닝 작업의 상태를 추적합니다.
 * 요구사항 35번~42번(단지/건물 도면 오브젝트, 텍스트, POI 추출)의 진행 상태를 담습니다.
 */
@Entity
@Table(name = "ai_job")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiJob {

    /**
     * AI 작업의 고유 식별자 (PK).
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * 작업을 요청한 테넌트.
     */
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    /**
     * 분석 대상이 되는 도면 이미지 엔티티.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "floorplan_id", nullable = false)
    private Floorplan floorplan;

    /**
     * 현재 작업의 처리 상태.
     * <p>'queued'(대기중), 'running'(분석중), 'succeeded'(완료됨), 'failed'(실패함).
     * 프론트엔드에서 폴링(Polling)이나 웹소켓으로 상태를 체크할 때 사용합니다.
     */
    @Column(nullable = false)
    private String status;

    /**
     * 분석에 사용된 AI 모델의 버전.
     * <p>나중에 모델이 업데이트되어 재분석이 필요할 때 필터링 기준으로 쓸 수 있습니다.
     */
    @Column(name = "model_version")
    private String modelVersion;

    /**
     * AI 분석 시 넘겨준 파라미터. (예: 어떤 객체만 추출할 것인지 등)
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> params;

    /**
     * 분석이 실제로 시작된 시각. (큐에서 꺼내진 시점)
     */
    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    /**
     * 분석이 끝난 시각 (성공/실패 무관).
     */
    @Column(name = "finished_at")
    private OffsetDateTime finishedAt;

    /**
     * 실패했을 경우 기록되는 에러 메시지 또는 스택트레이스.
     */
    private String error;

    @PrePersist
    void onPrePersist() {
        if (this.status == null) {
            this.status = "queued";
        }
    }

    @Builder
    public AiJob(UUID tenantId, Floorplan floorplan, String status, String modelVersion, Map<String, Object> params, OffsetDateTime startedAt, OffsetDateTime finishedAt, String error) {
        this.tenantId = tenantId;
        this.floorplan = floorplan;
        this.status = status != null ? status : "queued";
        this.modelVersion = modelVersion;
        this.params = params;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
        this.error = error;
    }
}

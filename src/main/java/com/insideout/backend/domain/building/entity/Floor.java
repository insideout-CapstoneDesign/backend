package com.insideout.backend.domain.building.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * 건물의 개별 층(Floor) 엔티티.
 *
 * <p>도면 데이터 호출(요구사항 9번), 층 전환 UI 표시 등에 기준이 되는 데이터입니다.
 */
@Entity
@Table(name = "floor")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Floor {

    /**
     * 층의 고유 식별자 (PK).
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * 이 층이 속한 테넌트 식별자.
     * <p>성능 및 파티셔닝 목적으로 다중 테넌트 환경에서 FK 없이 직접 저장합니다.
     */
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    /**
     * 이 층이 속한 건물.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "building_id", nullable = false)
    private Building building;

    /**
     * 숫자 형태의 층 레벨.
     * <p>지하 1층: -1, 1층: 1, 2층: 2 등으로 표현되어 층간 이동 계산에 사용됩니다.
     */
    @Column(nullable = false)
    private int level;

    /**
     * UI에 노출되는 층의 명칭.
     * <p>예시: "B1", "1F", "로비층".
     */
    @Column(nullable = false)
    private String name;

    /**
     * 해당 층의 실제 지표면 대비 고도(미터 단위).
     * <p>Z축이 포함된 3D 경로 계산이나 정밀한 계단/엘리베이터 이동 거리 계산 시 사용될 수 있습니다.
     */
    @Column(name = "elevation_m", precision = 10, scale = 2)
    private BigDecimal elevationM;

    @Builder
    public Floor(UUID tenantId, Building building, int level, String name, BigDecimal elevationM) {
        this.tenantId = tenantId;
        this.building = building;
        this.level = level;
        this.name = name;
        this.elevationM = elevationM;
    }
}

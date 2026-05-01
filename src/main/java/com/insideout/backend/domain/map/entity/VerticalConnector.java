package com.insideout.backend.domain.map.entity;

import com.insideout.backend.domain.building.entity.Building;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;
import java.util.UUID;

/**
 * 건물 내에서 층간 이동을 가능하게 해주는 수직 연결통로(수직 기둥) 엔티티.
 *
 * <p>엘리베이터, 계단, 에스컬레이터 등을 의미합니다.
 * 예를 들어 "1호기 엘리베이터"는 하나의 VerticalConnector이며,
 * 이것이 1층 노드, 2층 노드, 3층 노드를 관통하며 이어줍니다. (요구사항 21번 층 전환 핵심)
 */
@Entity
@Table(name = "vertical_connector")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VerticalConnector {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "map_version_id", nullable = false)
    private MapVersion mapVersion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "building_id", nullable = false)
    private Building building;

    /**
     * 연결통로 종류. (elevator, stair, escalator, ramp)
     */
    @Column(nullable = false)
    private String kind;

    /**
     * 명칭 (예: "중앙 엘리베이터 A호기", "비상계단 1").
     */
    private String name;

    /**
     * 수용 인원 (엘리베이터의 경우 라우팅 대기 시간 계산에 활용 가능).
     */
    private Integer capacity;

    /**
     * 평균 대기 시간(초). 길찾기 소요 시간에 합산됩니다.
     */
    @Column(name = "avg_wait_seconds")
    private Integer avgWaitSeconds;

    /**
     * 운행 방향. ('up', 'down', 'both')
     * <p>에스컬레이터의 경우 올라가는 것만 있을 수 있습니다.
     */
    private String direction;

    /**
     * 휠체어 접근 가능 여부 등 부가 속성.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> accessibility;

    @PrePersist
    void onPrePersist() {
        if (this.accessibility == null) {
            this.accessibility = Map.of();
        }
    }

    @Builder
    public VerticalConnector(UUID tenantId, MapVersion mapVersion, Building building, String kind, String name, Integer capacity, Integer avgWaitSeconds, String direction, Map<String, Object> accessibility) {
        this.tenantId = tenantId;
        this.mapVersion = mapVersion;
        this.building = building;
        this.kind = kind;
        this.name = name;
        this.capacity = capacity;
        this.avgWaitSeconds = avgWaitSeconds;
        this.direction = direction;
        this.accessibility = accessibility != null ? accessibility : Map.of();
    }
}

package com.insideout.backend.domain.map.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * 엣지(Edge)의 종류 사전 테이블.
 *
 * 마스터 데이터로 V5 시드 데이터에 INSERT 됨:
 * walkway(일반 보행), stair(계단), elevator(엘리베이터),
 * escalator(에스컬레이터, 단방향), door(문 통과), outdoor(실외 보행).
 *
 * <p>Edge 엔티티가 이 테이블의 code를 참조해서
 * "이 엣지는 보행로인지, 계단인지, 엘리베이터인지" 결정.
 */
@Entity
@Table(name = "edge_kind")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EdgeKind {

    /**
     * 엣지 종류 코드 (Primary Key).
     *
     * <p>예시: "walkway"(보행), "stair"(계단 이동), "elevator"(엘리베이터 이동).
     */
    @Id
    @Column(length = 50)
    private String code;

    /**
     * 한글 표시 이름.
     * <p>예시: "일반 보행", "계단 이동", "엘리베이터 이동".
     */
    @Column(name = "name_ko", nullable = false)
    private String nameKo;

    /**
     * 방향성이 있는 엣지인지.
     *
     * <p>true: 단방향 (예: 에스컬레이터는 한 방향으로만 이동 가능)
     * <br>false: 양방향 (대부분의 보행로, 계단)
     *
     * <p>라우팅 엔진이 그래프 만들 때 단방향 엣지는 한쪽으로만 연결.
     */
    @Column(name = "is_directed", nullable = false)
    private boolean isDirected;

    /**
     * 기본 가중치 배수.
     *
     * <p>A* 길찾기에서 이 종류의 엣지는 거리 × 이 배수만큼 비용으로 계산됨.
     *
     * <p>예시:
     * <ul>
     *   <li>walkway: 1.0 (기본)</li>
     *   <li>stair: 3.0 (계단은 힘들어서 회피 권장)</li>
     *   <li>elevator: 5.0 (대기시간 고려)</li>
     *   <li>door: 1.2 (문 통과 약간 페널티)</li>
     * </ul>
     */
    @Column(name = "default_cost_multiplier", nullable = false, precision = 10, scale = 2)
    private BigDecimal defaultCostMultiplier;

    @Builder
    public EdgeKind(String code, String nameKo, boolean isDirected, BigDecimal defaultCostMultiplier) {
        this.code = code;
        this.nameKo = nameKo;
        this.isDirected = isDirected;
        this.defaultCostMultiplier = defaultCostMultiplier != null ? defaultCostMultiplier : BigDecimal.ONE;
    }
}
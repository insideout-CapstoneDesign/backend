package com.insideout.backend.domain.map.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * 노드(Node)의 종류 사전 테이블.
 *
 * 마스터 데이터(시드 데이터로 미리 채워둠)이며, V5 마이그레이션에서
 * INSERT 됨: corridor, door, entrance, stair, elevator, escalator, poi_anchor, portal
 *
 * <p>이 테이블은 "어떤 종류의 노드들이 시스템에 존재하는가"를 정의함.
 * Node 엔티티가 이 테이블의 code 컬럼을 외래키로 참조.
 *
 * <p>비유: enum과 비슷하지만 DB에서 관리해서 운영 중에도 추가 가능.
 */
@Entity
@Table(name = "node_kind")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NodeKind {

    /**
     * 노드 종류 코드 (Primary Key).
     *
     * <p>예시: "corridor"(복도 교차점), "elevator"(엘리베이터),
     * "stair"(계단), "door"(문), "entrance"(건물 출입구).
     *
     * <p>UUID 대신 의미 있는 문자열을 PK로 사용 — 마스터 데이터라 변경 거의 없음.
     */
    @Id
    @Column(length = 50)
    private String code;

    /**
     * 한글 표시 이름.
     *
     * <p>예시: "복도 교차점", "엘리베이터", "계단".
     * 사용자에게 보여주는 텍스트.
     */
    @Column(name = "name_ko", nullable = false)
    private String nameKo;

    /**
     * 층간 이동 관련 노드인지 여부.
     *
     * <p>true: 엘리베이터, 계단, 에스컬레이터 (다른 층으로 연결됨)
     * <br>false: 일반 복도, 문 등 (같은 층 안에서만 의미 있음)
     *
     * <p>라우팅 엔진이 "이 노드는 층간 이동 가능"을 판단할 때 사용.
     */
    @Column(name = "is_vertical", nullable = false)
    private boolean isVertical;

    /**
     * vertical_connector로 묶여야 하는 노드인지 여부.
     *
     * <p>true: 엘리베이터, 계단 — 여러 층의 노드들이 하나의 "이동 수단"으로 묶여야 함
     * <br>false: 일반 노드
     *
     * <p>true인 노드는 반드시 VerticalConnector에 등록되어야 라우팅에서 제대로 동작.
     */
    @Column(name = "needs_connector", nullable = false)
    private boolean needsConnector;

    @Builder
    public NodeKind(String code, String nameKo, boolean isVertical, boolean needsConnector) {
        this.code = code;
        this.nameKo = nameKo;
        this.isVertical = isVertical;
        this.needsConnector = needsConnector;
    }
}

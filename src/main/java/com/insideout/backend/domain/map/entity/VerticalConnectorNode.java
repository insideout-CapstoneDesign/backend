package com.insideout.backend.domain.map.entity;

import com.insideout.backend.domain.building.entity.Floor;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * 엘리베이터/계단 통로(VerticalConnector)와 
 * 각 층의 출입구 노드(Node)를 연결해주는 매핑 테이블.
 *
 * <p>예를 들어 1호기 엘리베이터가 1층, 2층, 3층에서 멈춘다면
 * 이 테이블에 1층 노드, 2층 노드, 3층 노드가 각각 맵핑되어 기록됩니다.
 * 라우팅 엔진은 이 테이블을 보고 "아, 이 노드에서 엘리베이터를 타면 다른 층 노드로 갈 수 있구나"를 알게 됩니다.
 */
@Entity
@Table(name = "vertical_connector_node")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@IdClass(VerticalConnectorNodeId.class)
public class VerticalConnectorNode {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "connector_id", nullable = false)
    private VerticalConnector connector;

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "node_id", nullable = false)
    private Node node;

    /**
     * 해당 노드가 속한 층.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "floor_id", nullable = false)
    private Floor floor;

    // TODO: VerticalConnectorNode 생성 시 아래 3가지 불변식을 Service에서 반드시 검증해야 합니다.
    //       LAZY 로딩 특성상 이 생성자 안에서 연관 엔티티를 호출하면 LazyInitializationException이 발생합니다.
    //       Service에서 connector, node, floor를 완전히 로딩한 후 검증하세요:
    //
    //       1. floor가 node의 floor와 일치하는지:
    //          if (!node.getFloor().getId().equals(floor.getId()))
    //              throw new MapException(MapErrorCode.VERTICAL_CONNECTOR_FLOOR_MISMATCH);
    //
    //       2. tenantId가 connector의 tenantId와 일치하는지:
    //          if (!tenantId.equals(connector.getTenantId()))
    //              throw new MapException(MapErrorCode.VERTICAL_CONNECTOR_TENANT_MISMATCH);
    //
    //       3. tenantId가 node의 tenantId와 일치하는지:
    //          if (!tenantId.equals(node.getTenantId()))
    //              throw new MapException(MapErrorCode.VERTICAL_CONNECTOR_TENANT_MISMATCH);
    @Builder
    public VerticalConnectorNode(UUID tenantId, VerticalConnector connector, Node node, Floor floor) {
        this.tenantId = tenantId;
        this.connector = connector;
        this.node = node;
        this.floor = floor;
    }
}

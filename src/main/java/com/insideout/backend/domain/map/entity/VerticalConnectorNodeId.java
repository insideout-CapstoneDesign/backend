package com.insideout.backend.domain.map.entity;

import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

/**
 * VerticalConnectorNode 복합키 클래스.
 */
@NoArgsConstructor
@EqualsAndHashCode
public class VerticalConnectorNodeId implements Serializable {
    private UUID connector;
    private UUID node;
}

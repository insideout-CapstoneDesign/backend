package com.insideout.backend.domain.map.exception;

import com.insideout.backend.global.apiPayload.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum MapErrorCode implements BaseErrorCode {

    NEGATIVE_LENGTH(
        HttpStatus.BAD_REQUEST,
        "MAP400_1",
        "엣지의 물리적 거리(lengthM)는 0 이상이어야 합니다."
    ),
    INVALID_BASE_WEIGHT(
        HttpStatus.BAD_REQUEST,
        "MAP400_2",
        "엣지의 가중치(baseWeight)는 0보다 커야 합니다."
    ),
    BUILDING_MISMATCH(
        HttpStatus.BAD_REQUEST,
        "MAP400_3",
        "노드의 MapVersion과 Floor가 서로 다른 건물(Building)을 참조하고 있습니다."
    ),
    NEGATIVE_EXTRA_COST(
        HttpStatus.BAD_REQUEST,
        "MAP400_4",
        "장애물의 추가 비용(extraCost)은 0 이상이어야 합니다."
    ),
    INVALID_ACTIVE_PERIOD(
        HttpStatus.BAD_REQUEST,
        "MAP400_5",
        "장애물의 시작 시간(activeFrom)은 종료 시간(activeTo)보다 이후일 수 없습니다."
    ),
    VERTICAL_CONNECTOR_FLOOR_MISMATCH(
        HttpStatus.BAD_REQUEST,
        "MAP400_6",
        "VerticalConnectorNode의 floor와 node의 floor가 일치하지 않습니다."
    ),
    VERTICAL_CONNECTOR_TENANT_MISMATCH(
        HttpStatus.BAD_REQUEST,
        "MAP400_7",
        "VerticalConnectorNode의 tenantId가 connector 또는 node의 tenantId와 일치하지 않습니다."
    ),
    ZONE_TENANT_MISMATCH(
        HttpStatus.BAD_REQUEST,
        "MAP400_8",
        "Zone의 tenantId가 mapVersion 또는 floor의 tenantId와 일치하지 않습니다."
    );

    private final HttpStatus status;
    private final String code;
    private final String message;
}

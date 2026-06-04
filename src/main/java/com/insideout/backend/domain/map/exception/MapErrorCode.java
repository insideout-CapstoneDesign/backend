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
    ),
    OBSTACLE_TENANT_MISMATCH(
        HttpStatus.BAD_REQUEST,
        "MAP400_9",
        "장애물의 tenantId가 floor 또는 building의 tenant와 일치하지 않습니다."
    ),
    OBSTACLE_BUILDING_MISMATCH(
        HttpStatus.BAD_REQUEST,
        "MAP400_10",
        "장애물의 floor가 해당 building에 속하지 않습니다."
    ),
    PUBLISH_REQUIRES_ENTRANCE_CALIBRATION(
        HttpStatus.BAD_REQUEST,
        "MAP400_11",
        "출입구 캘리브레이션이 완료되지 않아 최종 배포할 수 없습니다."
    ),
    PUBLISH_REQUIRES_POI_EXTERNAL_MAPPING(
        HttpStatus.BAD_REQUEST,
        "MAP400_12",
        "외부 장소 매핑 검토가 끝나지 않은 POI가 있어 최종 배포할 수 없습니다."
    ),
    MAP_VERSION_NOT_FOUND(
        HttpStatus.NOT_FOUND,
        "MAP404_1",
        "해당 건물의 맵 버전을 찾을 수 없습니다."
    ),
    MAP_VERSION_NOT_EDITABLE(
        HttpStatus.BAD_REQUEST,
        "MAP400_13",
        "수정 가능한 초안 상태의 맵 버전만 변경할 수 있습니다."
    );

    private final HttpStatus status;
    private final String code;
    private final String message;
}

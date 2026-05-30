package com.insideout.backend.domain.building.exception;

import com.insideout.backend.global.apiPayload.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum BuildingErrorCode implements BaseErrorCode {

    NEGATIVE_ENTRANCE_COUNT(
        HttpStatus.BAD_REQUEST,
        "BUILDING400_1",
        "출입구 개수(entranceCount)는 0 이상이어야 합니다."
    ),
    INVALID_FLOORPLAN_DIMENSIONS(
        HttpStatus.BAD_REQUEST,
        "BUILDING400_2",
        "도면의 가로/세로 길이는 0보다 커야 합니다."
    ),
    TENANT_MISMATCH(
        HttpStatus.BAD_REQUEST,
        "BUILDING400_3",
        "도면의 테넌트 ID와 층의 테넌트 ID가 일치하지 않습니다."
    ),
    BUILDING_CAMPUS_TENANT_MISMATCH(
        HttpStatus.BAD_REQUEST,
        "BUILDING400_4",
        "건물의 테넌트와 캠퍼스의 테넌트가 일치하지 않습니다."
    ),
    BUILDING_DIRECTORY_CAMPUS_TENANT_MISMATCH(
        HttpStatus.BAD_REQUEST,
        "BUILDING400_5",
        "건물 디렉터리의 테넌트와 캠퍼스의 테넌트가 일치하지 않습니다."
    ),
    CAMPUS_MAP_TENANT_MISMATCH(
        HttpStatus.BAD_REQUEST,
        "BUILDING400_6",
        "캠퍼스 맵의 테넌트 ID와 캠퍼스의 테넌트 ID가 일치하지 않습니다."
    ),
    TENANT_NOT_FOUND(
        HttpStatus.NOT_FOUND,
        "BUILDING404_1",
        "해당 테넌트를 찾을 수 없습니다."
    ),
    CAMPUS_NOT_FOUND(
        HttpStatus.NOT_FOUND,
        "BUILDING404_2",
        "해당 캠퍼스를 찾을 수 없거나 권한이 없습니다."
    ),
    BUILDING_NOT_FOUND(
        HttpStatus.NOT_FOUND,
        "BUILDING404_3",
        "해당 건물을 찾을 수 없습니다."
    ),
    FLOOR_NOT_FOUND(
        HttpStatus.NOT_FOUND,
        "BUILDING404_4",
        "해당 층을 찾을 수 없습니다."
    ),
    BUILDING_ENTRANCE_NOT_FOUND(
        HttpStatus.NOT_FOUND,
        "BUILDING404_5",
        "해당 건물 출입구를 찾을 수 없습니다."
    ),
    UNAUTHORIZED_ACCESS(
        HttpStatus.FORBIDDEN,
        "BUILDING403_1",
        "해당 건물 정보에 접근 권한이 없습니다."
    ),
    INVALID_CAMPUS_BOUNDARY(
        HttpStatus.BAD_REQUEST,
        "BUILDING400_7",
        "캠퍼스 경계(boundary)는 최소 3개 이상의 유효한 좌표가 필요합니다."
    ),
    INVALID_CAMPUS_GATES(
        HttpStatus.BAD_REQUEST,
        "BUILDING400_8",
        "캠퍼스 출입구(gate)는 최소 1개 이상 필요합니다."
    ),
    INVALID_CAMPUS_GATE(
        HttpStatus.BAD_REQUEST,
        "BUILDING400_9",
        "유효하지 않은 Campus Gate입니다."
    ),
    DUPLICATE_CAMPUS_GATE_NAME(
        HttpStatus.BAD_REQUEST,
        "BUILDING400_12",
        "Campus Gate 이름은 중복될 수 없습니다."
    ),
    BUILDING_FLOOR_MISMATCH(
        HttpStatus.BAD_REQUEST,
        "BUILDING400_10",
        "해당 층은 요청한 건물에 속하지 않습니다."
    ),
    ENTRANCE_MAPPING_REQUIRES_CAMPUS(
        HttpStatus.BAD_REQUEST,
        "BUILDING400_11",
        "Campus Gate 매핑은 캠퍼스에 속한 건물에서만 가능합니다."
    ),
    POI_CATEGORY_NOT_FOUND(
        HttpStatus.NOT_FOUND,
        "BUILDING404_6",
        "필수 POI 카테고리를 찾을 수 없습니다."
    );

    private final HttpStatus status;
    private final String code;
    private final String message;
}

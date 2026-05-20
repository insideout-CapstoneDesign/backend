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
    );

    private final HttpStatus status;
    private final String code;
    private final String message;
}

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
    );

    private final HttpStatus status;
    private final String code;
    private final String message;
}

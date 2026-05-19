package com.insideout.backend.domain.navigation.exception;

import com.insideout.backend.global.apiPayload.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum NavigationErrorCode implements BaseErrorCode {

    TMAP_EMPTY_RESPONSE(
            HttpStatus.BAD_GATEWAY,
            "NAVIGATION502_1",
            "TMAP API 응답이 비어 있습니다."
    ),
    TMAP_REQUEST_FAILED(
            HttpStatus.BAD_GATEWAY,
            "NAVIGATION502_2",
            "TMAP API 요청 중 오류가 발생했습니다."
    ),
    TMAP_CONNECTION_FAILED(
            HttpStatus.BAD_GATEWAY,
            "NAVIGATION502_3",
            "TMAP API 서버와 통신할 수 없습니다."
    ),
    INVALID_TRANSIT_RESPONSE(
            HttpStatus.BAD_GATEWAY,
            "NAVIGATION502_4",
            "TMAP 대중교통 경로 응답 형식이 올바르지 않습니다."
    ),
    INVALID_CAR_RESPONSE(
            HttpStatus.BAD_GATEWAY,
            "NAVIGATION502_5",
            "TMAP 자동차 경로 응답 형식이 올바르지 않습니다."
    ),
    INVALID_WALK_RESPONSE(
            HttpStatus.BAD_GATEWAY,
            "NAVIGATION502_6",
            "TMAP 도보 경로 응답 형식이 올바르지 않습니다."
    ),
    INDOOR_ROUTE_NOT_FOUND(
            HttpStatus.OK,
            "NAVIGATION404_1",
            "실내 경로를 찾을 수 없습니다."
    ),
    CAMPUS_ROUTE_NOT_FOUND(
            HttpStatus.OK,
            "NAVIGATION404_2",
            "캠퍼스 내부 경로를 찾을 수 없습니다."
    ),
    ROUTE_NOT_FOUND(
            HttpStatus.OK,
            "NAVIGATION404_3",
            "경로를 찾을 수 없습니다."
    );

    private final HttpStatus status;
    private final String code;
    private final String message;
}

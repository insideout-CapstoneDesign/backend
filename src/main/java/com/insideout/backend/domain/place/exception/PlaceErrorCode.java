package com.insideout.backend.domain.place.exception;

import com.insideout.backend.global.apiPayload.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum PlaceErrorCode implements BaseErrorCode {

    INVALID_COORDINATE(
            HttpStatus.BAD_REQUEST,
            "PLACE400_1",
            "좌표 값이 올바르지 않습니다."
    ),
    INVALID_RADIUS(
            HttpStatus.BAD_REQUEST,
            "PLACE400_2",
            "반경(radius) 값이 올바르지 않습니다."
    ),
    KAKAO_LOCAL_API_UNAVAILABLE(
            HttpStatus.SERVICE_UNAVAILABLE,
            "PLACE503_1",
            "카카오 장소 서비스와 통신할 수 없습니다."
    );

    private final HttpStatus status;
    private final String code;
    private final String message;
}

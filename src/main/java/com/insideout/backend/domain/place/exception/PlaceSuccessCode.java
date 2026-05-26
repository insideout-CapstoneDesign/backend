package com.insideout.backend.domain.place.exception;

import com.insideout.backend.global.apiPayload.code.BaseSuccessCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum PlaceSuccessCode implements BaseSuccessCode {

    PLACE_INFO_NOT_AVAILABLE(
            HttpStatus.OK,
            "PLACE200_1",
            "해당 위치의 장소 정보를 찾을 수 없어요."
    );

    private final HttpStatus status;
    private final String code;
    private final String message;
}

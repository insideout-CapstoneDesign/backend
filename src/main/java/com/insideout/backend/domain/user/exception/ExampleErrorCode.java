package com.insideout.backend.domain.user.exception;

import org.springframework.http.HttpStatus;

import com.insideout.backend.global.apiPayload.code.BaseErrorCode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ExampleErrorCode implements BaseErrorCode {

    EXAMPLE_NOT_FOUND(HttpStatus.NOT_FOUND,
            "EXAMPLE404_1",
            "해당 예시를 찾을 수 없습니다."),
    ;

    private final HttpStatus status;
    private final String code;
    private final String message;
}

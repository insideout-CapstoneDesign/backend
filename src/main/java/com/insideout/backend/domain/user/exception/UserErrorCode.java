package com.insideout.backend.domain.user.exception;

import com.insideout.backend.global.apiPayload.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum UserErrorCode implements BaseErrorCode {

    EMAIL_ALREADY_EXISTS(
        HttpStatus.CONFLICT,
        "USER409_1",
        "이미 사용 중인 이메일입니다."
    ),
    DISPLAY_NAME_ALREADY_EXISTS(
        HttpStatus.CONFLICT,
        "USER409_2",
        "이미 사용 중인 닉네임입니다."
    ),
    LOGIN_FAILED(
        HttpStatus.UNAUTHORIZED,
        "USER401_1",
        "이메일 또는 비밀번호가 올바르지 않습니다."
    );

    private final HttpStatus status;
    private final String code;
    private final String message;
}

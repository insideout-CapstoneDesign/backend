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
    USER_NOT_FOUND(
        HttpStatus.NOT_FOUND,
        "USER404_1",
        "사용자를 찾을 수 없습니다."
    ),
    INVALID_PASSWORD(
        HttpStatus.UNAUTHORIZED,
        "USER401_1",
        "비밀번호가 일치하지 않습니다."
    );

    private final HttpStatus status;
    private final String code;
    private final String message;
}

package com.insideout.backend.domain.tenant.exception;

import com.insideout.backend.global.apiPayload.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum TenantErrorCode implements BaseErrorCode {

    TENANT_SLUG_CONFLICT(
            HttpStatus.CONFLICT,
            "BUILDING409_1",
            "slug명이 중복됩니다."
    );

    private final HttpStatus status;
    private final String code;
    private final String message;
}

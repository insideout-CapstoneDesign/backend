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
    ),
    TENANT_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "TENANT404_1",
            "해당 테넌트를 찾을 수 없습니다."
    ),
    MEMBERSHIP_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "TENANT404_2",
            "해당 테넌트의 멤버가 아닙니다."
    ),
    NOT_TENANT_OWNER(
            HttpStatus.FORBIDDEN,
            "TENANT403_1",
            "해당 테넌트의 소유자 권한이 없습니다."
    ),
    TENANT_HAS_ACTIVE_BUILDINGS(
            HttpStatus.BAD_REQUEST,
            "TENANT400_1",
            "활성화된 건물이 존재하여 구독을 취소할 수 없습니다. 건물들을 먼저 비활성화해 주세요."
    );

    private final HttpStatus status;
    private final String code;
    private final String message;
}

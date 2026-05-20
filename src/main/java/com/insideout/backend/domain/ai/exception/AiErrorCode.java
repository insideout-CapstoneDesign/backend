package com.insideout.backend.domain.ai.exception;

import com.insideout.backend.global.apiPayload.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum AiErrorCode implements BaseErrorCode {

    AI_TENANT_NOT_FOUND(HttpStatus.NOT_FOUND, "AI404_2", "로그인 사용자의 소속 테넌트를 찾을 수 없습니다."),
    FLOORPLAN_NOT_FOUND(HttpStatus.NOT_FOUND, "AI404_1", "분석할 도면을 찾을 수 없습니다."),
    AI_JOB_NOT_FOUND(HttpStatus.NOT_FOUND, "AI404_3", "AI 작업 정보를 찾을 수 없습니다."),
    AI_ANALYSIS_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "AI500_1", "AI 분석 처리 중 오류가 발생했습니다."),
    AI_INVALID_DETECTION_GEOMETRY(HttpStatus.BAD_GATEWAY, "AI502_4", "AI 서버가 유효하지 않은 도형 데이터를 반환했습니다."),
    AI_SERVER_UNREACHABLE(HttpStatus.BAD_GATEWAY, "AI502_1", "AI 서버에 연결할 수 없습니다."),
    AI_SERVER_ERROR(HttpStatus.BAD_GATEWAY, "AI502_2", "AI 서버에서 오류가 발생했습니다."),
    AI_EMPTY_RESPONSE(HttpStatus.BAD_GATEWAY, "AI502_3", "AI 서버 응답이 비어 있습니다."),
    ;

    private final HttpStatus status;
    private final String code;
    private final String message;
}

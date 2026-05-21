package com.insideout.backend.global.infra.storage.exception;

import com.insideout.backend.global.apiPayload.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum StorageErrorCode implements BaseErrorCode {

    UPLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "STORAGE500_1", "파일 업로드에 실패했습니다."),
    DOWNLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "STORAGE500_2", "파일 다운로드에 실패했습니다."),
    DELETE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "STORAGE500_3", "파일 삭제에 실패했습니다."),
    FILE_NOT_FOUND(HttpStatus.NOT_FOUND, "STORAGE404_1", "파일을 찾을 수 없습니다."),
    INVALID_FILE(HttpStatus.BAD_REQUEST, "STORAGE400_1", "유효하지 않은 파일입니다."),
    INVALID_FILE_EXTENSION(HttpStatus.BAD_REQUEST, "STORAGE400_2", "지원되지 않는 파일 확장자입니다. (png, jpg, jpeg만 허용)"),
    FILE_SIZE_EXCEEDED(HttpStatus.BAD_REQUEST, "STORAGE400_3", "파일 용량이 제한(10MB)을 초과했습니다."),
    ;

    private final HttpStatus status;
    private final String code;
    private final String message;
}

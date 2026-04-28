package com.insideout.backend.global.apiPayload;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.insideout.backend.global.apiPayload.code.BaseErrorCode;
import com.insideout.backend.global.apiPayload.code.BaseSuccessCode;
import com.insideout.backend.global.apiPayload.code.GeneralSuccessCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;

@Getter
@JsonPropertyOrder({"isSuccess","timestamp","code","message","result"})
public class ApiResponse<T> {

    @JsonProperty("isSuccess")
    private final Boolean isSuccess;

    @JsonProperty("code")
    private final String code;

    @JsonProperty("message")
    private final String message;

    @JsonProperty("timestamp")
    private final LocalDateTime timestamp;

    @JsonProperty("result")
    private T result;

    private ApiResponse(Boolean isSuccess, String code, String message, T result) {
        this.isSuccess = isSuccess;
        this.timestamp = LocalDateTime.now();
        this.code = code;
        this.message = message;
        this.result = result;
    }

    // [성공] 가장 많이 쓰는 200 OK (결과 데이터 있음)
    public static <T> ResponseEntity<ApiResponse<T>> OK(T result) {
        return of(GeneralSuccessCode.OK, result);
    }

    // [성공] 가장 많이 쓰는 200 OK (결과 데이터 없음)
    public static <T> ResponseEntity<ApiResponse<T>> OK() {
        return of(GeneralSuccessCode.OK, null);
    }

    // [성공] 201, 202 등 커스텀 성공 상태가 필요할 때
    public static <T> ResponseEntity<ApiResponse<T>> of(BaseSuccessCode code, T result) {
        if (code.getStatus() == HttpStatus.NO_CONTENT) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity
                .status(code.getStatus())
                .body(onSuccessBody(code, result));
    }

    // [실패] 에러 핸들러에서 사용 (데이터 없는 일반 에러)
    public static <T> ResponseEntity<ApiResponse<T>> onFailureEntity(BaseErrorCode code) {
        return ResponseEntity
                .status(code.getStatus())
                .body(onFailureBody(code, null));
    }

    // [실패] 에러 핸들러에서 사용 (데이터 포함 - 예: Validation 에러 메시지)
    public static <T> ResponseEntity<ApiResponse<T>> onFailureEntity(BaseErrorCode code, T result) {
        return ResponseEntity
                .status(code.getStatus())
                .body(onFailureBody(code, result));
    }

    private static <T> ApiResponse<T> onSuccessBody(BaseSuccessCode code, T result) {
        return new ApiResponse<>(true, code.getCode(), code.getMessage(), result);
    }

    private static <T> ApiResponse<T> onFailureBody(BaseErrorCode code, T result) {
        return new ApiResponse<>(false, code.getCode(), code.getMessage(), result);
    }
}

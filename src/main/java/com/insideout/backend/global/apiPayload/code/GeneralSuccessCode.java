package com.insideout.backend.global.apiPayload.code;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum GeneralSuccessCode implements BaseSuccessCode{

    OK(HttpStatus.OK,
            "COMMON200_1",
            "성공적으로 요청을 처리했습니다."),
    CREATED(HttpStatus.CREATED,
            "COMMON201_1",
            "새로운 리소스가 생성되었습니다."),
    ACCEPTED(HttpStatus.ACCEPTED,
            "COMMON202_1",
            "요청이 접수되었습니다."),
    NO_CONTENT(HttpStatus.NO_CONTENT,
            "COMMON204_1",
            "처리가 완료되었으며 반환할 데이터가 없습니다.")

    ;
    private final HttpStatus status;
    private final String code;
    private final String message;
}

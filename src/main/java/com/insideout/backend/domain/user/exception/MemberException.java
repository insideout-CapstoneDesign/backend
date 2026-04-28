package com.insideout.backend.domain.user.exception;

import com.insideout.backend.global.apiPayload.code.BaseErrorCode;
import com.insideout.backend.global.apiPayload.exception.ProjectException;

public class MemberException extends ProjectException {
    public MemberException(BaseErrorCode errorCode) {super(errorCode);}
}

package com.insideout.backend.domain.ai.exception;

import com.insideout.backend.global.apiPayload.code.BaseErrorCode;
import com.insideout.backend.global.apiPayload.exception.ProjectException;

public class AiException extends ProjectException {
    public AiException(BaseErrorCode errorCode) {
        super(errorCode);
    }
}

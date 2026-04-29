package com.insideout.backend.domain.user.exception;

import com.insideout.backend.global.apiPayload.exception.ProjectException;

public class ExampleException extends ProjectException {
    public ExampleException(ExampleErrorCode errorCode) {
        super(errorCode);
    }
}

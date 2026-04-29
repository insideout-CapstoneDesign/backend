package com.insideout.backend.domain.example.exception;

import com.insideout.backend.global.apiPayload.code.BaseErrorCode;
import com.insideout.backend.global.apiPayload.exception.ProjectException;

public class ExampleException extends ProjectException {
    public ExampleException(BaseErrorCode errorCode) {super(errorCode);}
}

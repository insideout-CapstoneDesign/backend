package com.insideout.backend.domain.user.exception;

import com.insideout.backend.global.apiPayload.exception.ProjectException;

public class UserException extends ProjectException {
    public UserException(UserErrorCode errorCode) {
        super(errorCode);
    }
}

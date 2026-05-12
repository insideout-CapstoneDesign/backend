package com.insideout.backend.domain.navigation.exception;

import com.insideout.backend.global.apiPayload.exception.ProjectException;

public class NavigationException extends ProjectException {

    public NavigationException(NavigationErrorCode errorCode) {
        super(errorCode);
    }

    public NavigationException(NavigationErrorCode errorCode, Throwable cause) {
        super(errorCode);
        initCause(cause);
    }
}

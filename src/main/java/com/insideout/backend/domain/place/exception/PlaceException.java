package com.insideout.backend.domain.place.exception;

import com.insideout.backend.global.apiPayload.exception.ProjectException;

public class PlaceException extends ProjectException {
    public PlaceException(PlaceErrorCode errorCode) {
        super(errorCode);
    }
}

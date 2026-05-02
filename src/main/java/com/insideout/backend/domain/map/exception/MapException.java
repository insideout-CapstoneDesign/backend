package com.insideout.backend.domain.map.exception;

import com.insideout.backend.global.apiPayload.exception.ProjectException;

public class MapException extends ProjectException {
    public MapException(MapErrorCode errorCode) {
        super(errorCode);
    }
}

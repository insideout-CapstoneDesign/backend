package com.insideout.backend.domain.building.exception;

import com.insideout.backend.global.apiPayload.code.BaseErrorCode;
import com.insideout.backend.global.apiPayload.exception.ProjectException;

public class BuildingException extends ProjectException {
    public BuildingException(BaseErrorCode errorCode) {
        super(errorCode);
    }
}

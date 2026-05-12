package com.insideout.backend.global.infra.storage.exception;

import com.insideout.backend.global.apiPayload.code.BaseErrorCode;
import com.insideout.backend.global.apiPayload.exception.ProjectException;

public class StorageException extends ProjectException {
    public StorageException(BaseErrorCode errorCode) {
        super(errorCode);
    }
}

package com.insideout.backend.domain.tenant.exception;

import com.insideout.backend.global.apiPayload.code.BaseErrorCode;
import com.insideout.backend.global.apiPayload.exception.ProjectException;

public class TenantException extends ProjectException {
    public TenantException(BaseErrorCode errorCode) {super(errorCode);}
}

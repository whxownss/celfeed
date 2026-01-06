package com.xowns.celfeed.exception;

import lombok.Getter;

@Getter
public class ApiException extends RuntimeException {
    private final ErrorCode errorCode;
    private final Object errorData;

    public ApiException(ErrorCode errorCode, Object errorData) {
        super("(" + errorData + ") " + errorCode.getMessage());
        this.errorCode = errorCode;
        this.errorData = errorData;
    }
}

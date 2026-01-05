package com.xowns.celfeed.exception;

import lombok.Getter;

@Getter
public class ApiException extends RuntimeException {
    private final ErrorCode errorCode;

    public ApiException(ErrorCode errorCode) {
        super("(" + errorCode.getStatus() + ") " + errorCode.getMessage());
        this.errorCode = errorCode;
    }
}

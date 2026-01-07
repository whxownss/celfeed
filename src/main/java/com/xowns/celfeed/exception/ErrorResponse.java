package com.xowns.celfeed.exception;

import lombok.Getter;

@Getter
public class ErrorResponse {
    private String errorMessage;
    private Object errorData;

    private ErrorResponse(String errorMessage, Object errorData) {
        this.errorMessage = errorMessage;
        this.errorData = errorData;
    }

    public static ErrorResponse of(String errorMessage, Object errorData) {
        return new ErrorResponse(errorMessage, errorData);
    }
}
package com.xowns.celfeed.exception;

import lombok.Getter;

@Getter
public class ErrorResponse {
    private Object errorData;
    private String errorMessage;

    private ErrorResponse(Object errorData, String errorMessage) {
        this.errorData = errorData;
        this.errorMessage = errorMessage;
    }

    public static ErrorResponse of(Object errorData, String errorMessage) {
        return new ErrorResponse(errorData, errorMessage);
    }
}
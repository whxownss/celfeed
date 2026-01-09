package com.xowns.celfeed.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import org.jspecify.annotations.Nullable;

@Getter
public class ErrorResponse {
    private String errorMessage;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private @Nullable Object errorData;

    private ErrorResponse(String errorMessage, Object errorData) {
        this.errorMessage = errorMessage;
        this.errorData = errorData;
    }

    public static ErrorResponse of(String errorMessage) {
        return of(errorMessage, null);
    }

    public static ErrorResponse of(String errorMessage, @Nullable Object errorData) {
        return new ErrorResponse(errorMessage, errorData);
    }
}
package com.xowns.celfeed.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import org.jspecify.annotations.Nullable;

@Getter
public class ApiResponse<T> {

    private boolean isSuccess;
    private String message;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private @Nullable T data;

    private ApiResponse(String message, T data) {
        this.isSuccess = true;
        this.message = message;
        this.data = data;
    }

    public static ApiResponse<Void> of(String message) {
        return of(message, null);
    }

    public static <D> ApiResponse<D> of(String message, @Nullable D data) {
        return new ApiResponse<>(message, data);
    }
}

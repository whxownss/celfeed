package com.xowns.celfeed.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import org.jspecify.annotations.Nullable;

@Getter
public class ApiResponse<T> extends BaseResponse {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private @Nullable T data;

    private ApiResponse(String message, T data) {
        super(true, message);
        this.data = data;
    }

    public static ApiResponse<Void> of(String message) {
        return of(message, null);
    }

    public static <D> ApiResponse<D> of(String message, @Nullable D data) {
        return new ApiResponse<>(message, data);
    }
}

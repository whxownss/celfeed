package com.xowns.celfeed.controller;

import lombok.Getter;

@Getter
public class ApiResponse<T> {
    private T data;
    private String message;

    private ApiResponse(String message, T data) {
        this.message = message;
        this.data = data;
    }

    public static <D> ApiResponse<D> of(String message, D data) {
        return new ApiResponse<>(message, data);
    }
}

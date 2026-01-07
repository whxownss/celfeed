package com.xowns.celfeed.controller;

import lombok.Getter;

@Getter
public class ApiResponse<T> {
    private T data;
    private String message;

    private ApiResponse(T data, String message) {
        this.data = data;
        this.message = message;
    }

    public static <D> ApiResponse<D> of(D data, String message) {
        return new ApiResponse<>(data, message);
    }
}

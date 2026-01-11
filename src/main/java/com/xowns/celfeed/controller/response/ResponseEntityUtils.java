package com.xowns.celfeed.controller.response;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

public abstract class ResponseEntityUtils {

    public static ResponseEntity<ApiResponse> ok(String message) {
        return ResponseEntity.ok(ApiResponse.of(message));
    }

    public static <T> ResponseEntity<ApiResponse<T>> ok(String message, T data) {
        return ResponseEntity.ok(ApiResponse.of(message, data));
    }

    public static ResponseEntity<ApiResponse> create(String message) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(message));
    }

    public static <T> ResponseEntity<ApiResponse<T>> create(String message, T data) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(message, data));
    }
}
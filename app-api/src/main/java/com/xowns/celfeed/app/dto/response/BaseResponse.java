package com.xowns.celfeed.app.dto.response;

import lombok.Getter;

@Getter
public abstract class BaseResponse {
    private boolean success;
    private String message;

    protected BaseResponse(boolean success, String message) {
        this.success = success;
        this.message = message;
    }
}

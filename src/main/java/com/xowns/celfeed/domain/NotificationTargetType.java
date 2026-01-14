package com.xowns.celfeed.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum NotificationTargetType {
    POST("/api/posts/");

    private String targetURI;
}

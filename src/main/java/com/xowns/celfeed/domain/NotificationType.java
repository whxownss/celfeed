package com.xowns.celfeed.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum NotificationType {
    LIKE_POST("/api/posts/", "님이 내 게시글에 좋아요를 눌렀습니다."),
    WRITE_POST("/api/posts/", "님이 게시글을 작성했습니다.")
    ;

    private String targetURI;
    private String message;
}
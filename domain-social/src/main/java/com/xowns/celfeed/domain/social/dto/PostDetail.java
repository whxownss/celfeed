package com.xowns.celfeed.domain.social.dto;

import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class PostDetail {

    private Long id;
    private String content;
    private String memberNickname;
    private Long likeCount;
    private boolean isLiked;
    private LocalDateTime createAt;
    private LocalDateTime updatedAt;

    public PostDetail(Long id,
                      String content,
                      String memberNickname,
                      Long likeCount,
                      boolean isLiked,
                      LocalDateTime createAt,
                      LocalDateTime updatedAt) {

        this.id = id;
        this.content = content;
        this.memberNickname = memberNickname;
        this.likeCount = likeCount;
        this.isLiked = isLiked;
        this.createAt = createAt;
        this.updatedAt = updatedAt;
    }
}

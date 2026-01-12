package com.xowns.celfeed.dto.post;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class PostDetailResponse {

    private Long id;
    private String content;
    private String memberNickname;
    private Long likeCount;
    private boolean isLiked;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;

    public PostDetailResponse(Long id, String content, String memberNickname, Long likeCount, boolean isLiked, LocalDateTime createAt, LocalDateTime updatedAt) {
        this.id = id;
        this.content = content;
        this.memberNickname = memberNickname;
        this.likeCount = likeCount;
        this.isLiked = isLiked;
        this.createAt = createAt;
        this.updatedAt = updatedAt;
    }
}

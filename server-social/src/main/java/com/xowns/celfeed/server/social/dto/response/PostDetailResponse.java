package com.xowns.celfeed.server.social.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.xowns.celfeed.domain.social.dto.PostDetail;
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

    private PostDetailResponse(Long id,
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

    public static PostDetailResponse of(PostDetail postDetail) {
        return new PostDetailResponse(
                postDetail.getId(),
                postDetail.getContent(),
                postDetail.getMemberNickname(),
                postDetail.getLikeCount(),
                postDetail.isLiked(),
                postDetail.getCreateAt(),
                postDetail.getUpdatedAt()
        );
    }
}

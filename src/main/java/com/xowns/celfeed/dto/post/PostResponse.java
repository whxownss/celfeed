package com.xowns.celfeed.dto.post;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.xowns.celfeed.domain.basic.Post;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class PostResponse {

    private Long id;
    private String content;
    private String memberNickname;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;

    private PostResponse(Long id, String content, String memberNickname, LocalDateTime createAt, LocalDateTime updatedAt) {
        this.id = id;
        this.content = content;
        this.memberNickname = memberNickname;
        this.createAt = createAt;
        this.updatedAt = updatedAt;
    }

    public static PostResponse of(Post post) {
        return new PostResponse(
                post.getId(),
                post.getContent(),
                post.getMember().getNickname(),
                post.getCreatedAt(),
                post.getUpdatedAt()
        );
    }
}

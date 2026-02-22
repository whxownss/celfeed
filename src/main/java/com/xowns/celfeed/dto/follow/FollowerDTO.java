package com.xowns.celfeed.dto.follow;

import lombok.Getter;

@Getter
public class FollowerDTO {
    private Long id;
    private Long followerId;

    public FollowerDTO(Long id, Long followerId) {
        this.id = id;
        this.followerId = followerId;
    }
}

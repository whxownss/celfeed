package com.xowns.celfeed.service.kafka;

import lombok.Getter;

import java.util.List;

@Getter
public class WritePostNotiMessage {

    private List<Long> followerIds;
    private Long writerId;
    private Long postId;

    public WritePostNotiMessage(List<Long> followerIds, Long writerId, Long postId) {
        this.followerIds = followerIds;
        this.writerId = writerId;
        this.postId = postId;
    }
}

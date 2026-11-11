package com.xowns.celfeed.server.consumer.listener;

public record WritePostFanoutEvent(
        String eventId,
        Long postId,
        Long authorId,
        String authorNickname,
        Long startFollowerId,
        Long endFollowerId
) {
}

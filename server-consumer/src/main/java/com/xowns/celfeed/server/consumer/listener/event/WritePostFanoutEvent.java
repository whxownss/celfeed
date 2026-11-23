package com.xowns.celfeed.server.consumer.listener.event;

public record WritePostFanoutEvent(
        String eventId,
        Long postId,
        Long authorId,
        String authorNickname,
        Long startFollowerId,
        Long endFollowerId
) {
}

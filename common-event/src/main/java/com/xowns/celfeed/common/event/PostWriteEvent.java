package com.xowns.celfeed.common.event;

import java.time.LocalDateTime;

public record PostWriteEvent(
        Long postId,
        Long memberId,
        String nickname,
        LocalDateTime occurredAt
) implements SocialEvent {
    @Override
    public String aggregateType() {
        return "POST";
    }

    @Override
    public Long aggregateId() {
        return postId;
    }
}

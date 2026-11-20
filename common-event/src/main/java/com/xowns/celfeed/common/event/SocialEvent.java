package com.xowns.celfeed.common.event;

public sealed interface SocialEvent
        permits WritePostEvent, LikePostEvent, FollowMemberEvent {
}

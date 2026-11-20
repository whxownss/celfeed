package com.xowns.celfeed.common.event;

public enum EventType {
    WRITE_POST(KafkaTopic.WRITE_POST),
    LIKE_POST(KafkaTopic.LIKE_POST),
    FOLLOW_MEMBER(KafkaTopic.FOLLOW_MEMBER)
    ;

    private final String topic;

    EventType(String topic) {
        this.topic = topic;
    }

    public String topic() {
        return topic;
    }
}

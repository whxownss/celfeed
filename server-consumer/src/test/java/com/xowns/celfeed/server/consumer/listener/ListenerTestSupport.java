package com.xowns.celfeed.server.consumer.listener;

import com.xowns.celfeed.domain.notification.entity.Notification;
import com.xowns.celfeed.domain.notification.entity.NotificationType;
import com.xowns.celfeed.domain.social.entity.Member;
import com.xowns.celfeed.domain.social.entity.MemberRole;
import com.xowns.celfeed.server.consumer.cache.IdempotencyCache.LockHandle;
import com.xowns.celfeed.server.consumer.cache.IdempotencyCache.LockState;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/** 리스너 단위 테스트 공용 헬퍼 */
final class ListenerTestSupport {

    static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder().build();

    static final LockHandle ACQUIRED = new LockHandle("lock", "token", LockState.ACQUIRED);
    static final LockHandle HELD_BY_OTHER = new LockHandle("lock", null, LockState.HELD_BY_OTHER);

    private ListenerTestSupport() {
    }

    static ConsumerRecord<String, String> record(String topic, String payload) {
        return new ConsumerRecord<>(topic, 0, 0L, "key", payload);
    }

    static ConsumerRecord<String, String> record(String topic, Object event) {
        return record(topic, OBJECT_MAPPER.writeValueAsString(event));
    }

    static Member member(long id, String nickname) {
        Member member = Member.create(nickname, nickname + "@test.com", "pw", MemberRole.FAN);
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }

    static Notification notification(long id, String eventId, long receiverId, NotificationType type) {
        Notification notification = Notification.create(eventId, receiverId, 2L, type, 30L);
        ReflectionTestUtils.setField(notification, "id", id);
        return notification;
    }

    /** DB 유니크 키(uk_notification_event_receiver) 중복 — 드라이버 메시지에 키 이름이 포함됨 */
    static DataIntegrityViolationException duplicateKey() {
        return new DataIntegrityViolationException("could not execute statement",
                new RuntimeException("Duplicate entry 'x' for key 'notification." + Notification.UK_EVENT_RECEIVER + "'"));
    }

    /** 유니크 키가 아닌 다른 제약 위반 (NOT NULL 등) */
    static DataIntegrityViolationException otherConstraint() {
        return new DataIntegrityViolationException("could not execute statement",
                new RuntimeException("Column 'actor_id' cannot be null"));
    }
}

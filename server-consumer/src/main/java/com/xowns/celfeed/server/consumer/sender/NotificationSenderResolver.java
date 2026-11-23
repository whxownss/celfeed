package com.xowns.celfeed.server.consumer.sender;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class NotificationSenderResolver {

    private final List<NotificationSender> notificationSenders;

    public NotificationSender resolve() {
        return notificationSenders.stream()
                .filter(NotificationSender::isSupported)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("지원하는 발송 수단이 없습니다."));
    }
}

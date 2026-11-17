package com.xowns.celfeed.server.consumer.sender;

import com.xowns.celfeed.server.consumer.dto.NotificationDto;

import java.util.List;

public interface NotificationSender {
    boolean isSupported();
    void send(NotificationDto sendData);
    void send(List<NotificationDto> sendData);
}

package com.xowns.celfeed.server.consumer.sender;

import com.xowns.celfeed.server.consumer.dto.NotificationDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class LogNotificationSender implements NotificationSender {

    @Override
    public boolean isSupported() {
        return true;
    }

    @Override
    public void send(NotificationDto sendData) {
        log.info("[SENDER] {}", sendData.getMessage());
    }

    @Override
    public void send(List<NotificationDto> sendData) {
        sendData.forEach(this::send);
    }
}

package com.xowns.celfeed.service;

import com.xowns.celfeed.dto.notification.NotificationResponse;
import com.xowns.celfeed.repository.EmitterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class EmitterService {

    private static final Long DEFAULT_TIMEOUT = 60 * 60 * 1000L;

    private final EmitterRepository emitterRepository;

    public SseEmitter subscribe(Long loginId, String lastEventId) {
        String emitterId = loginId + "_" + System.currentTimeMillis();
        SseEmitter emitter = emitterRepository.save(emitterId, new SseEmitter(DEFAULT_TIMEOUT));

        emitter.onCompletion(() -> emitterRepository.deleteById(emitterId));
        emitter.onTimeout(() -> emitterRepository.deleteById(emitterId));

        sendToClient(emitter, emitterId, "알림 구독 성공");

        if (!lastEventId.isEmpty()) {
            Map<String, Object> events = emitterRepository.findAllEventCacheStartWithByMemberId(String.valueOf(loginId));
            events.entrySet().stream()
                    .filter(entry -> lastEventId.compareTo(entry.getKey()) < 0)
                    .forEach(entry -> sendToClient(emitter, entry.getKey(), entry.getValue()));
        }

        return emitter;
    }

    public void sendNotifications(List<NotificationResponse> sendData) {
        sendData.forEach(this::sendNotification);
    }

    public void sendNotification(NotificationResponse data) {
        Map<String, SseEmitter> emitters =
                emitterRepository.findAllEmitterStartWithByMemberId(String.valueOf(data.getReceiverId()));

        emitters.forEach((emitterId, emitter) -> {
            emitterRepository.saveEventCache(emitterId, data);
            sendToClient(emitter, emitterId, data);
        });
    }

    private void sendToClient(SseEmitter emitter, String emitterId, Object data) {
        try {
            emitter.send(
                    SseEmitter.event()
                            .id(emitterId)
                            .data(data)
            );
        } catch (IOException e) {
            emitterRepository.deleteById(emitterId);
            emitter.complete();
        }
    }
}

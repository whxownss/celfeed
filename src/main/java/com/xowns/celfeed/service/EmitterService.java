package com.xowns.celfeed.service;

import com.xowns.celfeed.dto.notification.NotificationResponse;
import com.xowns.celfeed.repository.EmitterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Slf4j
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

        sendToClient(emitter, emitterId, emitterId, "알림 구독 성공");

        if (!lastEventId.isEmpty()) {
            Map<String, Object> events = emitterRepository.findAllEventCacheStartWithByMemberId(String.valueOf(loginId));
            events.keySet().stream()
                    .filter(eventId -> lastEventId.compareTo(eventId) < 0)
                    .sorted(Comparator.reverseOrder())
                    .forEach(eventId -> sendToClient(emitter, eventId, eventId, events.get(eventId)));
        }
        return emitter;
    }

    public void sendNotifications(List<NotificationResponse> sendData) {
        sendData.forEach(this::sendNotification);
    }

    public void sendNotification(NotificationResponse data) {
        String eventId = data.getReceiverId() + "_" + System.currentTimeMillis();
        emitterRepository.saveEvent(eventId, data); // 계속 쌓이는데 괜찮나?

        Map<String, SseEmitter> emitters =
                emitterRepository.findAllEmitterStartWithByMemberId(String.valueOf(data.getReceiverId()));

        emitters.forEach((emitterId, emitter) -> sendToClient(emitter, emitterId, eventId, data));
    }

    private void sendToClient(SseEmitter emitter, String emitterId, String eventId, Object data) {
        try {
            emitter.send(
                    SseEmitter.event()
                            .id(eventId)
                            .data(data)
            );
        } catch (IOException e) {
            emitterRepository.deleteById(emitterId);
            emitter.complete();
        }
    }
}

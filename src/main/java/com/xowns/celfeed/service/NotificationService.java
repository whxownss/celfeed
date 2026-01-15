package com.xowns.celfeed.service;

import com.xowns.celfeed.controller.EmitterRepository;
import com.xowns.celfeed.domain.*;
import com.xowns.celfeed.dto.SliceDTO;
import com.xowns.celfeed.dto.notification.NotificationBulkDTO;
import com.xowns.celfeed.dto.notification.NotificationResponse;
import com.xowns.celfeed.exception.ApiException;
import com.xowns.celfeed.exception.ErrorCode;
import com.xowns.celfeed.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationBulkRepository notificationBulkRepository;
    private final EmitterRepository emitterRepository;

    private final PostRepository postRepository;
    private final MemberRepository memberRepository;
    private final FollowRepository followRepository;

    public void requestLikePostNotification(Long postId, Long actorId) {
        Member actor = memberRepository.findById(actorId).orElse(null);
        if (actor == null) return;

        Post post = postRepository.findById(postId).orElse(null);
        if (post == null) return;

        Member receiver = post.getMember();
        if (actor.equals(receiver)) return;

        Notification savedNotification =
                createNotification(receiver, actor, NotificationType.LIKE_POST, post.getId());

        sendNotification(receiver.getId(), NotificationResponse.of(savedNotification));
    }

    @Transactional
    private Notification createNotification(Member receiver, Member actor, NotificationType type, Long targetId) {

        Notification notification = Notification.create(receiver, actor, type, targetId);
        return notificationRepository.save(notification);
    }

    public void requestWritePostNotification(Long postId) {
        Post post = postRepository.findById(postId).orElse(null);
        if (post == null) return;

        Member postMember = post.getMember();
        List<Follow> followers = followRepository.findByToMember(postMember);
        if (followers.isEmpty()) return;

        List<NotificationBulkDTO> bulkList = followers.stream()
                .map(follower ->
                        new NotificationBulkDTO(
                                follower.getFromMember().getId(),
                                postMember.getId(),
                                NotificationType.WRITE_POST.name(),
                                post.getId()
                        )
                ).toList();
        createNotifications(bulkList);

        // 10만건 기준 1442ms, (type, targetId)로 방금 저장한거만 가져오기
        List<Notification> sendData =
                notificationRepository.findByTypeAndTargetId(NotificationType.WRITE_POST, post.getId());

        sendNotifications(sendData);
    }

    @Transactional
    private void createNotifications(List<NotificationBulkDTO> bulkList) {
        notificationBulkRepository.batchInsert(bulkList);
    }

    public SliceDTO<NotificationResponse> findAll(Long loginId, Pageable pageable) {
        Member receiver = getMemberOrThrow(loginId);

        PageRequest pageRequest = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                                                    Sort.by("createdAt").descending());
        Slice<NotificationResponse> notifications = notificationRepository.findByReceiver(receiver, pageRequest)
                .map(NotificationResponse::of);

        return SliceDTO.of(notifications);
    }

    @Transactional
    public void readNotification(Long loginId, Long notificationId) {
        Member loginMember = getMemberOrThrow(loginId);

        notificationRepository.findById(notificationId)
                .filter(notification -> loginMember.equals(notification.getReceiver()))
                .ifPresent(Notification::read);
    }

    @Transactional
    public void deleteNotification(Long loginId, Long notificationId) {
        Member loginMember = getMemberOrThrow(loginId);

        notificationRepository.findById(notificationId)
                .filter(notification ->  loginMember.equals(notification.getReceiver()))
                .ifPresent(notificationRepository::delete);
    }

    // ====
    private Member getMemberOrThrow(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new ApiException(ErrorCode.MEMBER_NOT_FOUND));
    }

    // ===============

    private static final Long DEFAULT_TIMEOUT = 60 * 60 * 1000L;

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

    private void sendNotification(Long memberId, NotificationResponse data) {
        Map<String, SseEmitter> emitters = emitterRepository.findAllEmitterStartWithByMemberId(String.valueOf(memberId));
        emitters.forEach((emitterId, emitter) -> {
            emitterRepository.saveEventCache(emitterId, data);
            sendToClient(emitter, emitterId, data);
        });
    }

    private void sendNotifications(List<Notification> sendData) {
        System.out.println("@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@");
        sendData.forEach(data -> sendNotification(data.getReceiver().getId(), NotificationResponse.of(data)));
        System.out.println("@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@");
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

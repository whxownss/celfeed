package com.xowns.celfeed.service.event;

import com.xowns.celfeed.domain.*;
import com.xowns.celfeed.dto.notification.NotificationBulkDTO;
import com.xowns.celfeed.dto.notification.NotificationResponse;
import com.xowns.celfeed.repository.*;
import com.xowns.celfeed.service.EmitterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationListener {

    private final MemberRepository memberRepository;
    private final FollowRepository followRepository;
    private final PostRepository postRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationBulkRepository notificationBulkRepository;
    private final EmitterService emitterService;

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void requestLikePostNotification(LikePostEvent event) {
        Member actor = memberRepository.findById(event.getActorId()).orElse(null);
        if (actor == null) return;

        Post post = postRepository.findById(event.getPostId()).orElse(null);
        if (post == null) return;

        Member receiver = post.getMember();
        if (actor.equals(receiver)) return;

        Notification savedNotification = notificationRepository.save(
                Notification.create(receiver, actor, NotificationType.LIKE_POST, post.getId())
        );

        emitterService.sendNotification(NotificationResponse.of(savedNotification));
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void requestWritePostNotification(WritePostEvent event) {
        Post post = postRepository.findById(event.getPostId()).orElse(null);
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
        notificationBulkRepository.batchInsert(bulkList);

        // 10만건 기준 1442ms, (type, targetId)로 방금 저장한거만 가져오기
        List<NotificationResponse> sendData =
                notificationRepository.findByTypeAndTargetId(NotificationType.WRITE_POST, post.getId())
                        .stream().map(NotificationResponse::of).toList();

        emitterService.sendNotifications(sendData);
    }
}

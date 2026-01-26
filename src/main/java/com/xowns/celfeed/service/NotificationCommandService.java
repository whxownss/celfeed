package com.xowns.celfeed.service;

import com.xowns.celfeed.domain.*;
import com.xowns.celfeed.dto.notification.NotificationBulkDTO;
import com.xowns.celfeed.dto.notification.NotificationResponse;
import com.xowns.celfeed.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationCommandService {

    private final NotificationRepository notificationRepository;
    private final NotificationBulkRepository notificationBulkRepository;
    private final MemberRepository memberRepository;
    private final PostRepository postRepository;
    private final FollowRepository followRepository;
    private final LikeRepository likeRepository;
    private final EmitterService emitterService;

    @Transactional
    public void saveWritePostNotification(Long postId) {
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
        notificationBulkRepository.batchInsert(bulkList);

        // 10만건 기준 1442ms, (type, targetId)로 방금 저장한거만 가져오기
        List<NotificationResponse> sendData =
                notificationRepository.findByTypeAndTargetId(NotificationType.WRITE_POST, post.getId())
                        .stream().map(NotificationResponse::of).toList();

        emitterService.sendNotifications(sendData);
    }

    @Transactional
    public void saveLikePostNotification(Long likeId) {
        Like like = likeRepository.findGraphById(likeId).orElse(null);
        if (like == null) return;

        Member receiver = like.getPost().getMember();
        Member actor = like.getMember();
        if (actor.equals(receiver)) return;

        Notification savedNotification = notificationRepository.save(
                Notification.create(receiver, actor, NotificationType.LIKE_POST, like.getPost().getId())
        );

        emitterService.sendNotification(NotificationResponse.of(savedNotification));
    }
}

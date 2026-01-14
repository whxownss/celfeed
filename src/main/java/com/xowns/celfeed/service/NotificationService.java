package com.xowns.celfeed.service;

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

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationBulkRepository notificationBulkRepository;

    private final PostRepository postRepository;
    private final MemberRepository memberRepository;
    private final FollowRepository followRepository;

    public void requestLikePost(Long postId, Long actorId) {
        Member actor = memberRepository.findById(actorId).orElse(null);
        if (actor == null) return;

        Post post = postRepository.findById(postId).orElse(null);
        if (post == null) return;

        Member receiver = post.getMember();
        if (actor.equals(receiver)) return;

        createNotification(receiver, actor, NotificationType.LIKE_POST, NotificationTargetType.POST, post.getId());
    }

    @Transactional
    private void createNotification(Member receiver, Member actor, NotificationType type,
                                   NotificationTargetType targetType, Long targetId) {

        Notification notification = Notification.create(receiver, actor, type, targetType, targetId);
        notificationRepository.save(notification);
    }

    public void requestWritePost(Long postId) {
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
                                NotificationTargetType.POST.name(),
                                post.getId()
                        )
                ).toList();

        createNotifications(bulkList);
    }

    @Transactional
    private void createNotifications(List<NotificationBulkDTO> bulkList) {
        notificationBulkRepository.batchInsert(bulkList);
    }

    public SliceDTO<NotificationResponse> findAll(Long loginId, Pageable pageable) {
        Member receiver = getMemberOrThrow(loginId);

        PageRequest pageRequest = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                                                    Sort.by("createdAt").descending());
        Slice<NotificationResponse> notifications = notificationRepository.findAllByReceiver(receiver, pageRequest)
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
}

package com.xowns.celfeed.service.notification;

import com.xowns.celfeed.config.sharding.Sharding;
import com.xowns.celfeed.config.sharding.ShardingTarget;
import com.xowns.celfeed.domain.basic.Member;
import com.xowns.celfeed.dto.SliceDTO;
import com.xowns.celfeed.dto.notification.NotificationResponse;
import com.xowns.celfeed.exception.ApiException;
import com.xowns.celfeed.exception.ErrorCode;
import com.xowns.celfeed.domain.notification.Notification;
import com.xowns.celfeed.repository.notification.NotificationRepository;
import com.xowns.celfeed.repository.basic.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(value = "notificationTransactionManager", readOnly = true)
@Sharding(target = ShardingTarget.NOTIFICATION)
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final MemberRepository memberRepository;

    public SliceDTO<NotificationResponse> findAll(Long loginId, Pageable pageable) {
        Member receiver = getMemberOrThrow(loginId);

        PageRequest pageRequest = PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                Sort.by("createdAt").descending()
        );
        Slice<NotificationResponse> notifications = notificationRepository
                .findByReceiver(receiver, LocalDate.now().minusDays(29).atStartOfDay(), pageRequest)
                .map(NotificationResponse::of);

        return SliceDTO.of(notifications);
    }

    @Transactional(value = "notificationTransactionManager")
    public void readNotification(Long loginId, Long notificationId) {
        Member loginMember = getMemberOrThrow(loginId);

        notificationRepository.findById(notificationId)
                .filter(notification -> loginMember.equals(notification.getReceiver()))
                .ifPresent(Notification::read);
    }

    @Transactional(value = "notificationTransactionManager")
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

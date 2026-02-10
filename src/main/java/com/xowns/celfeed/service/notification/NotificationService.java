package com.xowns.celfeed.service.notification;

import com.xowns.celfeed.config.sharding.Sharding;
import com.xowns.celfeed.config.sharding.ShardingTarget;
import com.xowns.celfeed.domain.basic.Member;
import com.xowns.celfeed.dto.SliceDTO;
import com.xowns.celfeed.dto.member.MemberIdNickname;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

        // 알림 조회
        Slice<Notification> notifications = notificationRepository.findByReceiver(
                receiver.getId(),
                LocalDate.now().minusDays(29).atStartOfDay(),
                pageRequest
        );

        // 조회한 알림의 actor id, nickname 조회
        List<Long> actorIds = notifications.map(Notification::getActorId).toList();
        Map<Long, String> idNicknameMap = memberRepository.findByIdIn(actorIds)
                .stream().collect(Collectors.toMap(
                        MemberIdNickname::getId,
                        MemberIdNickname::getNickname
                ));

        // 응답용 알림 데이터 생성
        Slice<NotificationResponse> notificationResponses =
                notifications.map(notification -> NotificationResponse.of(
                        notification,
                        idNicknameMap.get(notification.getActorId()
                )));

        return SliceDTO.of(notificationResponses);
    }

    @Transactional(value = "notificationTransactionManager")
    public void readNotification(Long loginId, Long notificationId) {
        Member loginMember = getMemberOrThrow(loginId);

        notificationRepository.findById(notificationId)
                .filter(notification -> loginMember.getId().equals(notification.getReceiverId()))
                .ifPresent(Notification::read);
    }

    @Transactional(value = "notificationTransactionManager")
    public void deleteNotification(Long loginId, Long notificationId) {
        Member loginMember = getMemberOrThrow(loginId);

        notificationRepository.findById(notificationId)
                .filter(notification ->  loginMember.getId().equals(notification.getReceiverId()))
                .ifPresent(notificationRepository::delete);
    }

    // ====
    private Member getMemberOrThrow(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new ApiException(ErrorCode.MEMBER_NOT_FOUND));
    }
}

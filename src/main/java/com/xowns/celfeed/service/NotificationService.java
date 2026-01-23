package com.xowns.celfeed.service;

import com.xowns.celfeed.repository.EmitterRepository;
import com.xowns.celfeed.domain.*;
import com.xowns.celfeed.dto.SliceDTO;
import com.xowns.celfeed.dto.notification.NotificationBulkDTO;
import com.xowns.celfeed.dto.notification.NotificationResponse;
import com.xowns.celfeed.exception.ApiException;
import com.xowns.celfeed.exception.ErrorCode;
import com.xowns.celfeed.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final MemberRepository memberRepository;

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
}

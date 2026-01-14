package com.xowns.celfeed;

import com.xowns.celfeed.domain.*;
import com.xowns.celfeed.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.w3c.dom.stylesheets.LinkStyle;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
public class TestDataInit {
    private final MemberRepository memberRepository;
    private final PostRepository postRepository;
    private final LikeRepository likeRepository;
    private final FollowRepository followRepository;
    private final NotificationRepository notificationRepository;

    //@EventListener(ApplicationReadyEvent.class)
    public void initNotification() {
        likePost(1L, 1L, 51L, 55L);
        writePost(1L, 2L, 2);

        likePost(1L, 1L, 56L, 60L);
        writePost(1L, 2L, 3);

        likePost(1L, 1L, 61L, 70L);
        writePost(1L, 2L, 1);

        likePost(1L, 1L, 71L, 75L);
        writePost(1L, 2L, 4);
    }

    private void likePost(Long receiverId, Long postId, Long startFanId, Long endFanId) {
        Member receiver = memberRepository.findById(receiverId).get();
        Post post = postRepository.findById(postId).get();

        for (Long i = startFanId; i <= endFanId; i++) {
            Member actor = memberRepository.findById(i).get();
            Notification notification = Notification.create(
                    receiver,
                    actor,
                    NotificationType.LIKE_POST,
                    NotificationTargetType.POST,
                    post.getId()
            );
            notificationRepository.save(notification);
        }
    }

    private void writePost(Long receiverId, Long actorId, int cnt) {
        Member receiver = memberRepository.findById(receiverId).get();

        for (int i = 0; i < cnt; i++) {
            Member actor = memberRepository.findById(actorId).get();
            Post post2 = Post.create(actor, "게시글" + i);
            postRepository.save(post2);

            Notification notification = Notification.create(
                    receiver,
                    actor,
                    NotificationType.WRITE_POST,
                    NotificationTargetType.POST,
                    post2.getId()
            );
            notificationRepository.save(notification);
        }
    }

    //@EventListener(ApplicationReadyEvent.class)
    public void initMemberData() {
        for (int i = 1; i <= 50; i++) {
            memberRepository.save(Member.create("kanye" + i, "naver" + i, "1234123", MemberRole.CELEB));
        }
        Member celeb1 = memberRepository.findById(1L).get();

        for (int i = 1; i <= 100_000; i++) {
            Member fan = Member.create("fan" + i, "goog" + i, "1234123", MemberRole.FAN);
            memberRepository.save(fan);
            followRepository.save(Follow.create(fan, celeb1));
        }

        Post post1 = Post.create(celeb1, "첫번째게시글");
        postRepository.save(post1);

        Post post2 = Post.create(celeb1, "두번째게시글");
        postRepository.save(post2);

        Member fan = null;
        for (int i = 51; i <= 65; i++) {
            fan = memberRepository.findById(Long.valueOf(i)).get();
            likeRepository.save(Like.create(post1, fan));
        }
        for (int i = 60; i <= 80; i++) {
            fan = memberRepository.findById(Long.valueOf(i)).get();
            likeRepository.save(Like.create(post2, fan));
        }
    }
}

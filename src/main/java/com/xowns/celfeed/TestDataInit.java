package com.xowns.celfeed;

import com.xowns.celfeed.domain.*;
import com.xowns.celfeed.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
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
    private final JdbcTemplate jdbcTemplate;

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
                    post2.getId()
            );
            notificationRepository.save(notification);
        }
    }

    //@EventListener(ApplicationReadyEvent.class)
    public void initMemberData() {
        saveCeleb();
        Member celeb1 = memberRepository.findById(1L).get();

        saveFanAndFollow(100_000, celeb1);

        Post post1 = writePost(celeb1, "첫번째 게시글");
        Post post2 = writePost(celeb1, "두번째 게시글");

        likePost(post1, 51, 65);
        likePost(post2, 60, 80);
    }
    private void saveCeleb() {
        for (int i = 1; i <= 50; i++) {
            memberRepository.save(Member.create("kanye" + i, "naver" + i, "1234123", MemberRole.CELEB));
        }
    }
    private void saveFanAndFollow(int count, Member celeb) {
        for (int i = 1; i <= count; i++) {
            Member fan = Member.create("fan" + i, "goog" + i, "1234123", MemberRole.FAN);
            memberRepository.save(fan);
            followRepository.save(Follow.create(fan, celeb));
        }
    }
    private Post writePost(Member member, String content) {
        Post post = Post.create(member, content);
        return postRepository.save(post);
    }
    private void likePost(Post post, int memberIdStart, int memberIdEnd) {
        for (int i = memberIdStart; i <= memberIdEnd; i++) {
            Member fan = memberRepository.findById(Long.valueOf(i)).get();
            likeRepository.save(Like.create(post, fan));
        }
    }

//    @EventListener(ApplicationReadyEvent.class)
    public void initMemberData2() {
//        getCelebAndFollowCeleb(3L, 100051L, 600050L);
//        getCelebAndFollowCeleb(4L, 600051L, 1100050L);
//        getCelebAndFollowCeleb(5L, 400051L, 1100050L);
//        getCelebAndFollowCeleb(6L, 100051L, 400050L);

//        getCelebAndFollowCeleb(7L, 51L, 50050L);
//        getCelebAndFollowCeleb(8L, 50051L, 80050L);
//        getCelebAndFollowCeleb(9L, 80051L, 90050L);
//        getCelebAndFollowCeleb(10L, 90051L, 100050L);
    }
    private void saveFan() {
        List<FanDTO> fanList = new ArrayList<>();

        for (int i = 400_001; i <= 1_000_000; i++) {
            fanList.add(new FanDTO("fn" + i, "email" + i, "1234123","FAN"));
        }

        String sql = "insert into" +
                " member (nickname, email, password, role, created_at, updated_at)" +
                " values (?, ?, ?, ?, now(), now())";

        jdbcTemplate.batchUpdate(sql, fanList, fanList.size(), (ps, argument) -> {
            ps.setString(1, argument.nickname);
            ps.setString(2, argument.email);
            ps.setString(3, argument.password);
            ps.setString(4, argument.role);
        });
    }
    private void getCelebAndFollowCeleb(Long celebId, Long startId, Long endId) {
        Member celeb = memberRepository.findById(Long.valueOf(celebId)).get();

//        List<FollowDTO> followDTOS = new ArrayList<>();
//        List<Member> fans = memberRepository.findByIdBetweenAndRole(startId, endId, MemberRole.FAN);
//        for (Member member : fans) {
//            followDTOS.add(new FollowDTO(member.getId(), celeb.getId()));
//        }
//        System.out.println("@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@");
//        String sql = "insert into" +
//                " follow (from_id, to_id, created_at)" +
//                " values (?, ?, now())";
//        jdbcTemplate.batchUpdate(sql, followDTOS, followDTOS.size(), (ps, argument) -> {
//            ps.setLong(1, argument.fromId);
//            ps.setLong(2, argument.toId);
//        });
//        System.out.println("@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@");
    }

    static class FanDTO {
        private String nickname;
        private String email;
        private String password;
        private String role;

        public FanDTO(String nickname, String email, String password, String role) {
            this.nickname = nickname;
            this.email = email;
            this.password = password;
            this.role = role;
        }
    }

    static class FollowDTO {
        private Long fromId;
        private Long toId;

        public FollowDTO(Long fromId, Long toId) {
            this.fromId = fromId;
            this.toId = toId;
        }
    }
}

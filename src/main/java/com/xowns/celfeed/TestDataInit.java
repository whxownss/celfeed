package com.xowns.celfeed;

import com.xowns.celfeed.domain.basic.*;
import com.xowns.celfeed.repository.basic.FollowRepository;
import com.xowns.celfeed.repository.basic.LikeRepository;
import com.xowns.celfeed.repository.basic.MemberRepository;
import com.xowns.celfeed.repository.basic.PostRepository;
import com.xowns.celfeed.repository.notification.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
public class TestDataInit {
    private final MemberRepository memberRepository;
    private final PostRepository postRepository;
    private final LikeRepository likeRepository;
    private final FollowRepository followRepository;
    private final NotificationRepository notificationRepository;
    private final JdbcTemplate jdbcTemplate;

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

        List<Member> members = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            Member fan = Member.create("fan" + i, "goog" + i, "1234123", MemberRole.FAN);
            members.add(fan);
        }
        String sql = "insert into " +
                " member (created_at, updated_at, email, nickname, password, role) " +
                " values (now(), now(),?, ?, ?, ?)";
        jdbcTemplate.batchUpdate(sql, members, members.size(), (ps, argument) -> {
            ps.setString(1, argument.getEmail());
            ps.setString(2, argument.getNickname());
            ps.setString(3, argument.getPassword());
            ps.setString(4, argument.getRole().name());
        });

        for (long i = 51; i <= count + 51 - 1; i++) {
            Member fan = memberRepository.findById(i).get();
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
    public void init() {
//        saveCeleb();
//        saveFan(1_000_000);
//        getCelebAndFollowCeleb(1L, 51L, 10_050L);
//        getCelebAndFollowCeleb(2L, 51L, 100_050L);
//        getCelebAndFollowCeleb(3L, 51L, 1_000_050L);
        getCelebAndFollowCeleb(13L, 51L, 1_050L);
        getCelebAndFollowCeleb(14L, 51L, 150L);
    }

    private void saveFan(int cnt) {
        List<FanDTO> fanList = new ArrayList<>();

        for (int i = 1; i <= cnt; i++) {
            fanList.add(new FanDTO("fan" + i, "goog" + i, "1234123","FAN"));
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
//        Member celeb = memberRepository.findById(Long.valueOf(celebId)).get();
//
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

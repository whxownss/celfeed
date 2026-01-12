package com.xowns.celfeed;

import com.xowns.celfeed.domain.Like;
import com.xowns.celfeed.domain.Member;
import com.xowns.celfeed.domain.MemberRole;
import com.xowns.celfeed.domain.Post;
import com.xowns.celfeed.repository.LikeRepository;
import com.xowns.celfeed.repository.MemberRepository;
import com.xowns.celfeed.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

@RequiredArgsConstructor
public class TestDataInit {
    private final MemberRepository memberRepository;
    private final PostRepository postRepository;
    private final LikeRepository likeRepository;

    @EventListener(ApplicationReadyEvent.class)
    public void initMemberData() {
        for (int i = 1; i <= 50; i++) {
            memberRepository.save(Member.create("kanye" + i, "naver" + i, "1234123", MemberRole.CELEB));
        }

        for (int i = 1; i <= 30; i++) {
            memberRepository.save(Member.create("fan" + i, "goog" + i, "1234123", MemberRole.FAN));
        }

        Member celeb1 = memberRepository.findById(1L).get();

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

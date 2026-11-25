package com.xowns.celfeed.server.social.support;

import com.xowns.celfeed.domain.social.entity.*;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;

/**
 * 단위 테스트용 엔티티 생성 헬퍼
 * (id / 생성 시각은 DB 가 채우는 값이라 리플렉션으로 설정)
 */
public final class Fixtures {

    private Fixtures() {
    }

    public static Member member(long id, String nickname) {
        return member(id, nickname, MemberRole.FAN);
    }

    public static Member member(long id, String nickname, MemberRole role) {
        Member member = Member.create(nickname, nickname + "@test.com", "password", role);
        ReflectionTestUtils.setField(member, "id", id);
        ReflectionTestUtils.setField(member, "createdAt", LocalDateTime.of(2026, 11, 1, 10, 0));
        return member;
    }

    public static Post post(long id, Member writer, String content) {
        Post post = Post.create(writer, content);
        ReflectionTestUtils.setField(post, "id", id);
        return post;
    }

    public static Like like(long id, Post post, Member member) {
        Like like = Like.create(post, member);
        ReflectionTestUtils.setField(like, "id", id);
        return like;
    }

    public static Follow follow(long id, Member from, Member to) {
        Follow follow = Follow.create(from, to);
        ReflectionTestUtils.setField(follow, "id", id);
        return follow;
    }

    /** 요청 DTO 는 setter/생성자가 없어서 JSON 으로 만든다 (실제 요청과 같은 방식) */
    public static <T> T fromJson(String json, Class<T> type) {
        return new ObjectMapper().readValue(json, type);
    }
}

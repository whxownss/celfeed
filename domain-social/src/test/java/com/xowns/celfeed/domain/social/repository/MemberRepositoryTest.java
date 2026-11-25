package com.xowns.celfeed.domain.social.repository;

import com.xowns.celfeed.domain.social.dto.MemberIdNickname;
import com.xowns.celfeed.domain.social.entity.Member;
import com.xowns.celfeed.domain.social.entity.MemberRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MemberRepositoryTest extends SocialRepositoryTestSupport {

    @Autowired
    MemberRepository memberRepository;

    @Test
    @DisplayName("저장하면 생성/수정 시각이 채워진다")
    void auditTimestamps() {
        Member member = member("철수", MemberRole.FAN);
        flushAndClear();

        Member found = memberRepository.findById(member.getId()).orElseThrow();
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("닉네임/이메일 존재 여부를 확인할 수 있다")
    void exists() {
        member("철수", MemberRole.FAN);
        flushAndClear();

        assertThat(memberRepository.existsByNickname("철수")).isTrue();
        assertThat(memberRepository.existsByNickname("영희")).isFalse();
        assertThat(memberRepository.existsByEmail("철수@test.com")).isTrue();
        assertThat(memberRepository.existsByEmail("영희@test.com")).isFalse();
    }

    @Test
    @DisplayName("이메일로 회원을 조회할 수 있다")
    void findByEmail() {
        Member member = member("철수", MemberRole.FAN);
        flushAndClear();

        assertThat(memberRepository.findByEmail("철수@test.com"))
                .get().extracting(Member::getId).isEqualTo(member.getId());
        assertThat(memberRepository.findByEmail("없음@test.com")).isEmpty();
    }

    @Test
    @DisplayName("닉네임 접두사 + 역할로 검색하면, 해당 역할의 회원만 정렬 순서대로 조회된다")
    void findByNicknameStartingWithAndRole() {
        member("celeb-b", MemberRole.CELEB);
        member("celeb-a", MemberRole.CELEB);
        member("celeb-fan", MemberRole.FAN);  // 역할이 다름
        member("other", MemberRole.CELEB);    // 접두사가 다름
        flushAndClear();

        Slice<Member> result = memberRepository.findByNicknameStartingWithAndRole(
                "celeb", MemberRole.CELEB, PageRequest.of(0, 10, Sort.by("nickname").ascending()));

        assertThat(result.getContent()).extracting(Member::getNickname).containsExactly("celeb-a", "celeb-b");
    }

    @Test
    @DisplayName("id 목록으로 id + 닉네임만 조회할 수 있다 (인터페이스 프로젝션)")
    void findByIdIn() {
        Member a = member("철수", MemberRole.FAN);
        Member b = member("영희", MemberRole.FAN);
        member("민수", MemberRole.FAN);
        flushAndClear();

        List<MemberIdNickname> result = memberRepository.findByIdIn(List.of(a.getId(), b.getId()));

        assertThat(result).extracting(MemberIdNickname::getId, MemberIdNickname::getNickname)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(a.getId(), "철수"),
                        org.assertj.core.groups.Tuple.tuple(b.getId(), "영희"));
    }

    @Test
    @DisplayName("닉네임이 중복되면 저장에 실패한다")
    void duplicateNickname() {
        memberRepository.saveAndFlush(Member.create("철수", "a@test.com", "pw", MemberRole.FAN));

        assertThatThrownBy(() ->
                memberRepository.saveAndFlush(Member.create("철수", "b@test.com", "pw", MemberRole.FAN)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("이메일이 중복되면 저장에 실패한다")
    void duplicateEmail() {
        memberRepository.saveAndFlush(Member.create("철수", "same@test.com", "pw", MemberRole.FAN));

        assertThatThrownBy(() ->
                memberRepository.saveAndFlush(Member.create("영희", "same@test.com", "pw", MemberRole.FAN)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}

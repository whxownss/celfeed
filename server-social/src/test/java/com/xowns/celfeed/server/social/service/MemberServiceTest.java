package com.xowns.celfeed.server.social.service;

import com.xowns.celfeed.common.web.dto.PageDTO;
import com.xowns.celfeed.common.web.dto.SliceDTO;
import com.xowns.celfeed.common.web.exception.ApiException;
import com.xowns.celfeed.common.web.exception.ErrorCode;
import com.xowns.celfeed.domain.social.entity.Member;
import com.xowns.celfeed.domain.social.entity.MemberRole;
import com.xowns.celfeed.domain.social.repository.MemberRepository;
import com.xowns.celfeed.server.social.dto.request.MemberLoginRequest;
import com.xowns.celfeed.server.social.dto.request.MemberRequest;
import com.xowns.celfeed.server.social.dto.response.MemberResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static com.xowns.celfeed.server.social.support.Fixtures.fromJson;
import static com.xowns.celfeed.server.social.support.Fixtures.member;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    @Mock
    MemberRepository memberRepository;

    @InjectMocks
    MemberService memberService;

    private static MemberRequest joinRequest() {
        return fromJson("""
                {"nickname": "철수", "email": "chulsoo@test.com", "password": "password1"}
                """, MemberRequest.class);
    }

    private static MemberLoginRequest loginRequest(String email, String password) {
        return fromJson("{\"email\": \"%s\", \"password\": \"%s\"}".formatted(email, password), MemberLoginRequest.class);
    }

    private static void assertErrorCode(Throwable e, ErrorCode expected) {
        assertThat(e).isInstanceOf(ApiException.class);
        assertThat(((ApiException) e).getErrorCode()).isEqualTo(expected);
    }

    // ========================================================================================

    @Test
    @DisplayName("회원가입하면 FAN 역할로 저장하고 id 를 반환한다")
    void join() {
        given(memberRepository.save(any(Member.class))).willAnswer(invocation -> {
            Member member = invocation.getArgument(0);
            ReflectionTestUtils.setField(member, "id", 1L);
            return member;
        });

        Long id = memberService.join(joinRequest());

        ArgumentCaptor<Member> saved = ArgumentCaptor.forClass(Member.class);
        verify(memberRepository).save(saved.capture());
        assertThat(id).isEqualTo(1L);
        assertThat(saved.getValue().getNickname()).isEqualTo("철수");
        assertThat(saved.getValue().getEmail()).isEqualTo("chulsoo@test.com");
        assertThat(saved.getValue().getRole()).isEqualTo(MemberRole.FAN);
    }

    @Test
    @DisplayName("닉네임이 중복이면 DUPLICATE_NICKNAME 예외를 던지고 저장하지 않는다")
    void joinDuplicateNickname() {
        given(memberRepository.existsByNickname("철수")).willReturn(true);

        assertThatThrownBy(() -> memberService.join(joinRequest()))
                .satisfies(e -> assertErrorCode(e, ErrorCode.DUPLICATE_NICKNAME));
        verify(memberRepository, never()).save(any());
    }

    @Test
    @DisplayName("이메일이 중복이면 DUPLICATE_EMAIL 예외를 던지고 저장하지 않는다")
    void joinDuplicateEmail() {
        given(memberRepository.existsByEmail("chulsoo@test.com")).willReturn(true);

        assertThatThrownBy(() -> memberService.join(joinRequest()))
                .satisfies(e -> assertErrorCode(e, ErrorCode.DUPLICATE_EMAIL));
        verify(memberRepository, never()).save(any());
    }

    @Test
    @DisplayName("회원 단건 조회 : 없으면 MEMBER_NOT_FOUND")
    void findOne() {
        given(memberRepository.findById(1L)).willReturn(Optional.of(member(1L, "철수")));
        given(memberRepository.findById(2L)).willReturn(Optional.empty());

        MemberResponse response = memberService.findOne(1L);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getNickname()).isEqualTo("철수");
        assertThat(response.getRole()).isEqualTo("FAN");
        assertThatThrownBy(() -> memberService.findOne(2L))
                .satisfies(e -> assertErrorCode(e, ErrorCode.MEMBER_NOT_FOUND));
    }

    @Test
    @DisplayName("회원 목록은 페이지 정보를 포함해 반환한다")
    void findAll() {
        PageRequest pageable = PageRequest.of(0, 2);
        given(memberRepository.findAll(pageable))
                .willReturn(new PageImpl<>(List.of(member(1L, "a"), member(2L, "b")), pageable, 5));

        PageDTO<MemberResponse> result = memberService.findAll(pageable);

        assertThat(result.getContent()).extracting(MemberResponse::getNickname).containsExactly("a", "b");
        assertThat(result.getTotalElements()).isEqualTo(5);
        assertThat(result.getTotalPages()).isEqualTo(3);
    }

    @Test
    @DisplayName("닉네임 검색은 CELEB 만, 요청 정렬과 무관하게 닉네임 오름차순으로 조회한다")
    void findAllByNickname() {
        given(memberRepository.findByNicknameStartingWithAndRole(eq("cel"), eq(MemberRole.CELEB), any()))
                .willReturn(new SliceImpl<>(List.of(member(1L, "celeb", MemberRole.CELEB))));

        SliceDTO<MemberResponse> result =
                memberService.findAllByNickname("cel", PageRequest.of(2, 5, Sort.by("id").descending()));

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(memberRepository).findByNicknameStartingWithAndRole(eq("cel"), eq(MemberRole.CELEB), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(5);
        assertThat(pageable.getValue().getSort()).isEqualTo(Sort.by("nickname").ascending());
        assertThat(result.getContent()).extracting(MemberResponse::getNickname).containsExactly("celeb");
    }

    @Test
    @DisplayName("로그인 : 이메일과 비밀번호가 맞으면 회원 id 를 반환한다")
    void login() {
        given(memberRepository.findByEmail("a@test.com")).willReturn(Optional.of(member(1L, "a")));

        assertThat(memberService.login(loginRequest("a@test.com", "password"))).isEqualTo(1L);
    }

    @Test
    @DisplayName("로그인 : 이메일이 없거나 비밀번호가 틀리면 같은 LOGIN_FAIL 예외를 던진다 (어느 쪽이 틀렸는지 노출하지 않음)")
    void loginFail() {
        given(memberRepository.findByEmail("a@test.com")).willReturn(Optional.of(member(1L, "a")));
        given(memberRepository.findByEmail("none@test.com")).willReturn(Optional.empty());

        assertThatThrownBy(() -> memberService.login(loginRequest("a@test.com", "wrong")))
                .satisfies(e -> assertErrorCode(e, ErrorCode.LOGIN_FAIL));
        assertThatThrownBy(() -> memberService.login(loginRequest("none@test.com", "password")))
                .satisfies(e -> assertErrorCode(e, ErrorCode.LOGIN_FAIL));
    }
}

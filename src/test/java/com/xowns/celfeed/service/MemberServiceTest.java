package com.xowns.celfeed.service;

import com.xowns.celfeed.dto.MemberDTO;
import com.xowns.celfeed.dto.MemberResponse;
import com.xowns.celfeed.exception.ApiException;
import com.xowns.celfeed.exception.ErrorCode;
import com.xowns.celfeed.repository.MemberRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    @InjectMocks
    MemberService memberService;

    @Mock
    MemberRepository memberRepository;

    @Test
    @DisplayName("닉네임 중복 아니면 통과")
    void validateDuplicateNicknameSuccess() {
        // given
        String nickname = "nickname1";
        when(memberRepository.existsByNickname(nickname)).thenReturn(false);

        // when
        memberService.validateDuplicateNickname(nickname);

        // then
        verify(memberRepository).existsByNickname(nickname);
        verifyNoMoreInteractions(memberRepository);
    }

    @Test
    @DisplayName("닉네임 중복이면 예외")
    void validateDuplicateNicknameFail() {
        // given
        String nickname = "nickname1";
        when(memberRepository.existsByNickname(nickname)).thenReturn(true);

        // when
        assertThatThrownBy(() -> memberService.validateDuplicateNickname(nickname))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.DUPLICATE_NICKNAME);

        // then
        verify(memberRepository).existsByNickname(nickname);
        verifyNoMoreInteractions(memberRepository);
    }

    @Test
    @DisplayName("이메일 중복 아니면 통과")
    void validateDuplicateEmailSuccess() {
        // given
        String email = "email1@google.com";
        when(memberRepository.existsByEmail(email)).thenReturn(false);

        // when
        memberService.validateDuplicateEmail(email);

        // then
        verify(memberRepository).existsByEmail(email);
        verifyNoMoreInteractions(memberRepository);
    }

    @Test
    @DisplayName("이메일 중복이면 예외")
    void validateDuplicateEmailFail() {
        // given
        String email = "email1@google.com";
        when(memberRepository.existsByEmail(email)).thenReturn(true);

        // when
        assertThatThrownBy(() -> memberService.validateDuplicateEmail(email))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.DUPLICATE_EMAIL);

        // then
        verify(memberRepository).existsByEmail(email);
        verifyNoMoreInteractions(memberRepository);
    }

    @Test
    @DisplayName("닉네임, 이메일 중복 아니면 통과")
    void join() {
        // given
    }
}
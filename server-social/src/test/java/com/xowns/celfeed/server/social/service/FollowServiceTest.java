package com.xowns.celfeed.server.social.service;

import com.xowns.celfeed.common.event.FollowMemberEvent;
import com.xowns.celfeed.common.event.SocialEvent;
import com.xowns.celfeed.common.web.dto.SliceDTO;
import com.xowns.celfeed.common.web.exception.ApiException;
import com.xowns.celfeed.common.web.exception.ErrorCode;
import com.xowns.celfeed.domain.social.entity.Follow;
import com.xowns.celfeed.domain.social.entity.Member;
import com.xowns.celfeed.domain.social.repository.FollowRepository;
import com.xowns.celfeed.domain.social.repository.MemberRepository;
import com.xowns.celfeed.server.social.dto.response.MemberResponse;
import com.xowns.celfeed.server.social.outbox.OutboxEventRecorder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.SliceImpl;

import java.util.List;
import java.util.Optional;

import static com.xowns.celfeed.server.social.support.Fixtures.follow;
import static com.xowns.celfeed.server.social.support.Fixtures.member;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FollowServiceTest {

    @Mock
    FollowRepository followRepository;

    @Mock
    MemberRepository memberRepository;

    @Mock
    OutboxEventRecorder outboxEventRecorder;

    @InjectMocks
    FollowService followService;

    private final Member fan = member(1L, "fan");
    private final Member celeb = member(2L, "celeb");

    private void givenMembers() {
        given(memberRepository.findById(1L)).willReturn(Optional.of(fan));
        given(memberRepository.findById(2L)).willReturn(Optional.of(celeb));
    }

    @Test
    @DisplayName("새로 팔로우하면 저장하고, FollowMemberEvent(followId, from, to)를 Outbox 에 기록한다")
    void followNewMember() {
        givenMembers();
        given(followRepository.findByFromMemberAndToMember(fan, celeb)).willReturn(Optional.empty());
        given(followRepository.save(any(Follow.class))).willReturn(follow(10L, fan, celeb));

        Long followId = followService.followMember(1L, 2L);

        assertThat(followId).isEqualTo(10L);
        ArgumentCaptor<SocialEvent> event = ArgumentCaptor.forClass(SocialEvent.class);
        verify(outboxEventRecorder).record(event.capture());
        FollowMemberEvent recorded = (FollowMemberEvent) event.getValue();
        assertThat(recorded.getFollowId()).isEqualTo(10L);
        assertThat(recorded.getFromId()).isEqualTo(1L);
        assertThat(recorded.getToId()).isEqualTo(2L);
        assertThat(recorded.getEventId()).isNotBlank();
        assertThat(recorded.getOccurredAt()).isNotNull();
    }

    @Test
    @DisplayName("이미 팔로우 중이면 기존 id 를 반환하고, 저장/이벤트 기록을 하지 않는다 (중복 알림 방지)")
    void alreadyFollowing() {
        givenMembers();
        given(followRepository.findByFromMemberAndToMember(fan, celeb)).willReturn(Optional.of(follow(10L, fan, celeb)));

        assertThat(followService.followMember(1L, 2L)).isEqualTo(10L);

        verify(followRepository, never()).save(any());
        verifyNoInteractions(outboxEventRecorder);
    }

    @Test
    @DisplayName("자기 자신은 팔로우할 수 없다")
    void selfFollow() {
        assertThatThrownBy(() -> followService.followMember(1L, 1L))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(ErrorCode.SELF_FOLLOW_NOT_ALLOWED);
        verifyNoInteractions(followRepository, outboxEventRecorder);
    }

    @Test
    @DisplayName("대상 회원이 없으면 MEMBER_NOT_FOUND")
    void targetNotFound() {
        given(memberRepository.findById(1L)).willReturn(Optional.of(fan));
        given(memberRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> followService.followMember(1L, 99L))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
        verifyNoInteractions(outboxEventRecorder);
    }

    @Test
    @DisplayName("언팔로우 : 팔로우 중이면 삭제하고, 아니면 아무 일도 없다")
    void unfollow() {
        givenMembers();
        Follow follow = follow(10L, fan, celeb);
        given(followRepository.findByFromMemberAndToMember(fan, celeb))
                .willReturn(Optional.of(follow))
                .willReturn(Optional.empty());

        followService.unfollowMember(1L, 2L);
        followService.unfollowMember(1L, 2L);

        verify(followRepository, times(1)).delete(follow);
    }

    @Test
    @DisplayName("팔로잉 목록은 대상 회원을, 팔로워 목록은 팔로우한 회원을 반환한다")
    void followingAndFollowers() {
        givenMembers();
        PageRequest pageable = PageRequest.of(0, 10);
        given(followRepository.findByFromMember(fan, pageable))
                .willReturn(new SliceImpl<>(List.of(follow(10L, fan, celeb))));
        given(followRepository.findByToMember(celeb, pageable))
                .willReturn(new SliceImpl<>(List.of(follow(10L, fan, celeb))));

        SliceDTO<MemberResponse> following = followService.getFollowingMembers(1L, pageable);
        SliceDTO<MemberResponse> followers = followService.getFollowerMembers(2L, pageable);

        assertThat(following.getContent()).extracting(MemberResponse::getNickname).containsExactly("celeb");
        assertThat(followers.getContent()).extracting(MemberResponse::getNickname).containsExactly("fan");
    }
}

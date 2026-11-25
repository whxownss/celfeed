package com.xowns.celfeed.server.social.controller;

import com.xowns.celfeed.common.web.consts.SessionConst;
import com.xowns.celfeed.common.web.dto.SliceDTO;
import com.xowns.celfeed.common.web.exception.ApiException;
import com.xowns.celfeed.common.web.exception.ErrorCode;
import com.xowns.celfeed.domain.social.dto.PostDetail;
import com.xowns.celfeed.server.social.dto.response.MemberResponse;
import com.xowns.celfeed.server.social.dto.response.PostDetailResponse;
import com.xowns.celfeed.server.social.service.FollowService;
import com.xowns.celfeed.server.social.service.MemberService;
import com.xowns.celfeed.server.social.service.PostService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.SliceImpl;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static com.xowns.celfeed.server.social.support.Fixtures.member;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest
class PostAndFollowControllerTest {

    private static final long LOGIN_ID = 1L;

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    MemberService memberService;

    @MockitoBean
    PostService postService;

    @MockitoBean
    FollowService followService;

    private static MockHttpSession loginSession() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionConst.LOGIN_MEMBER, LOGIN_ID);
        return session;
    }

    @Nested
    @DisplayName("게시글")
    class Posts {

        @Test
        @DisplayName("게시글 API 는 로그인이 필요하다")
        void requiresLogin() throws Exception {
            mockMvc.perform(post("/api/posts").contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"hello world\"}"))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(get("/api/posts/10")).andExpect(status().isUnauthorized());
            verifyNoInteractions(postService);
        }

        @Test
        @DisplayName("작성하면 201 과 게시글 id 를 내려주고, 로그인 회원 id 로 서비스를 호출한다")
        void create() throws Exception {
            given(postService.write(eq(LOGIN_ID), any())).willReturn(10L);

            mockMvc.perform(post("/api/posts").session(loginSession())
                            .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"hello world\"}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data").value(10));
        }

        @Test
        @DisplayName("내용이 비었거나 5자 미만이면 400 (빈 값은 NotBlank 메시지 우선)")
        void contentValidation() throws Exception {
            mockMvc.perform(post("/api/posts").session(loginSession())
                            .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorData[0].message").value("글 내용을 입력해 주세요."));

            mockMvc.perform(patch("/api/posts/10").session(loginSession())
                            .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"abcd\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorData[0].message").value("글 내용은 5자 이상 250자 이내로 입력해 주세요."));

            verifyNoInteractions(postService);
        }

        /**
         * 현재 동작 기록 : PostDetailResponse 필드명이 createAt(오타로 보임), boolean isLiked 는 Lombok getter 때문에 liked 로 내려간다.
         */
        @Test
        @DisplayName("[현재 동작] 게시글 상세 응답의 작성일 필드는 createAt, 좋아요 여부 필드는 liked 다")
        void detailResponseFieldNames() throws Exception {
            LocalDateTime createdAt = LocalDateTime.of(2026, 11, 25, 9, 30, 0);
            given(postService.findOneDetail(LOGIN_ID, 10L)).willReturn(PostDetailResponse.of(
                    new PostDetail(10L, "내용", "writer", 3L, true, createdAt, createdAt)));

            mockMvc.perform(get("/api/posts/10").session(loginSession()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.likeCount").value(3))
                    .andExpect(jsonPath("$.data.liked").value(true))
                    .andExpect(jsonPath("$.data.isLiked").doesNotExist())
                    .andExpect(jsonPath("$.data.createAt").value("2026-11-25 09:30:00"))
                    .andExpect(jsonPath("$.data.createdAt").doesNotExist());
        }

        @Test
        @DisplayName("없는 게시글이면 404")
        void notFound() throws Exception {
            willThrow(new ApiException(ErrorCode.POST_NOT_FOUND)).given(postService).findOneDetail(LOGIN_ID, 10L);

            mockMvc.perform(get("/api/posts/10").session(loginSession()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorMessage").value("게시글이 존재하지 않습니다."));
        }

        @Test
        @DisplayName("수정/삭제/좋아요/좋아요 취소는 로그인 회원 id 와 게시글 id 로 서비스를 호출한다")
        void commands() throws Exception {
            mockMvc.perform(patch("/api/posts/10").session(loginSession())
                            .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"updated content\"}"))
                    .andExpect(status().isOk());
            mockMvc.perform(delete("/api/posts/10").session(loginSession())).andExpect(status().isOk());
            mockMvc.perform(post("/api/posts/10/likes").session(loginSession())).andExpect(status().isCreated());
            mockMvc.perform(delete("/api/posts/10/likes").session(loginSession())).andExpect(status().isOk());

            verify(postService).updatePost(eq(LOGIN_ID), eq(10L), any());
            verify(postService).deletePost(LOGIN_ID, 10L);
            verify(postService).likePost(LOGIN_ID, 10L);
            verify(postService).unlikePost(LOGIN_ID, 10L);
        }
    }

    @Nested
    @DisplayName("팔로우")
    class Follows {

        @Test
        @DisplayName("팔로우하면 201, 언팔로우하면 200")
        void followAndUnfollow() throws Exception {
            mockMvc.perform(post("/api/follows/2").session(loginSession()))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.message").value("팔로우를 걸었습니다."));
            mockMvc.perform(delete("/api/follows/2").session(loginSession()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("팔로우를 취소했습니다."));

            verify(followService).followMember(LOGIN_ID, 2L);
            verify(followService).unfollowMember(LOGIN_ID, 2L);
        }

        @Test
        @DisplayName("자기 자신을 팔로우하면 400")
        void selfFollow() throws Exception {
            willThrow(new ApiException(ErrorCode.SELF_FOLLOW_NOT_ALLOWED)).given(followService).followMember(LOGIN_ID, LOGIN_ID);

            mockMvc.perform(post("/api/follows/" + LOGIN_ID).session(loginSession()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorMessage").value("자기 자신을 팔로우할 수 없습니다."));
        }

        @Test
        @DisplayName("팔로잉/팔로워 목록을 조회한다")
        void lists() throws Exception {
            given(followService.getFollowingMembers(eq(LOGIN_ID), any()))
                    .willReturn(SliceDTO.of(new SliceImpl<>(List.of(MemberResponse.of(member(2L, "celeb"))))));
            given(followService.getFollowerMembers(eq(LOGIN_ID), any()))
                    .willReturn(SliceDTO.of(new SliceImpl<>(List.of(MemberResponse.of(member(3L, "fan"))))));

            mockMvc.perform(get("/api/follows/following").session(loginSession()))
                    .andExpect(jsonPath("$.data.content[0].nickname").value("celeb"));
            mockMvc.perform(get("/api/follows/followers").session(loginSession()))
                    .andExpect(jsonPath("$.data.content[0].nickname").value("fan"));
        }
    }
}

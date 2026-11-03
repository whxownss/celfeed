package com.xowns.celfeed.server.social.controller;

import com.xowns.celfeed.server.social.dto.request.PostRequest;
import com.xowns.celfeed.server.social.dto.response.PostDetailResponse;
import com.xowns.celfeed.server.social.dto.response.PostResponse;
import com.xowns.celfeed.server.social.service.PostService;
import com.xowns.celfeed.common.web.argumentresolver.Login;
import com.xowns.celfeed.common.web.dto.ApiResponse;
import com.xowns.celfeed.common.web.dto.SliceDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

import static org.springframework.http.HttpStatus.*;

@Slf4j
@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    @PostMapping
    @ResponseStatus(CREATED)
    public ApiResponse<Long> createPost(@Login Long loginId, @Valid @RequestBody PostRequest postRequest) {
        Long postId = postService.write(loginId, postRequest);
        return ApiResponse.of("게시글 작성이 완료되었습니다.", postId);
    }

    @GetMapping("/{postId}")
    @ResponseStatus(OK)
    public ApiResponse<PostDetailResponse> getPost(@Login Long loginId, @PathVariable Long postId) {
        PostDetailResponse findPost = postService.findOneDetail(loginId, postId);
        return ApiResponse.of("게시글이 조회되었습니다", findPost);
    }

    @PatchMapping("/{postId}")
    @ResponseStatus(OK)
    public ApiResponse<Void> updatePost(@Login Long loginId,
                                        @PathVariable Long postId,
                                        @Valid @RequestBody PostRequest postRequest) {

        postService.updatePost(loginId, postId, postRequest);
        return ApiResponse.of("게시글이 수정되었습니다.");
    }

    @DeleteMapping("/{postId}")
    @ResponseStatus(OK)
    public ApiResponse<Void> deletePost(@Login Long loginId, @PathVariable Long postId) {
        postService.deletePost(loginId, postId);
        return ApiResponse.of("게시글이 삭제되었습니다.");
    }

    @GetMapping
    @ResponseStatus(OK)
    public ApiResponse<SliceDTO<PostResponse>> getPosts(@RequestParam Long memberId, Pageable pageable) {
        SliceDTO<PostResponse> findPosts = postService.findAll(memberId, pageable);
        return ApiResponse.of("게시글 목록이 조회되었습니다.", findPosts);
    }

    @PostMapping("/{postId}/likes")
    @ResponseStatus(CREATED)
    public ApiResponse<Void> likePost(@Login Long loginId, @PathVariable Long postId) {
        postService.likePost(loginId, postId);
        return ApiResponse.of("게시글 좋아요");
    }

    @DeleteMapping("/{postId}/likes")
    @ResponseStatus(OK)
    public ApiResponse<Void> unlikePost(@Login Long loginId, @PathVariable Long postId) {
        postService.unlikePost(loginId, postId);
        return ApiResponse.of("게시글 좋아요 취소");
    }
}

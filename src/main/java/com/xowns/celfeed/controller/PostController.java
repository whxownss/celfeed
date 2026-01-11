package com.xowns.celfeed.controller;

import com.xowns.celfeed.common.argumentresolver.Login;
import com.xowns.celfeed.controller.response.ApiResponse;
import com.xowns.celfeed.controller.response.ResponseEntityUtils;
import com.xowns.celfeed.dto.post.PostRequest;
import com.xowns.celfeed.dto.post.PostResponse;
import com.xowns.celfeed.dto.SliceDTO;
import com.xowns.celfeed.service.PostService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    @PostMapping
    public ResponseEntity<ApiResponse<Long>> createPost(@Login Long loginId, @Valid @RequestBody PostRequest postRequest) {
        return ResponseEntityUtils.create("게시글 작성이 완료되었습니다.", postService.write(loginId, postRequest));
    }

    @GetMapping("/{postId}")
    public ResponseEntity<ApiResponse<PostResponse>> getPost(@PathVariable Long postId) {
        return ResponseEntityUtils.ok("게시글이 조회되었습니다", postService.findOne(postId));
    }

    @PatchMapping("/{postId}")
    public ResponseEntity<ApiResponse> updatePost(@Login Long loginId, @PathVariable Long postId, @Valid @RequestBody PostRequest postRequest) {
        postService.updatePost(loginId, postId, postRequest);
        return ResponseEntityUtils.ok("게시글이 수정되었습니다.");
    }

    @DeleteMapping("/{postId}")
    public ResponseEntity<ApiResponse> deletePost(@Login Long loginId, @PathVariable Long postId) {
        postService.deletePost(loginId, postId);
        return ResponseEntityUtils.ok("게시글이 삭제되었습니다.");
    }

    @GetMapping
    public ResponseEntity<ApiResponse<SliceDTO<PostResponse>>> getPosts(@RequestParam Long memberId, Pageable pageable) {
        return ResponseEntityUtils.ok("게시글 목록이 조회되었습니다.", postService.findAll(memberId, pageable));
    }

}

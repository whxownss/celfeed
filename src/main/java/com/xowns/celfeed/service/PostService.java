package com.xowns.celfeed.service;

import com.xowns.celfeed.domain.Member;
import com.xowns.celfeed.domain.Post;
import com.xowns.celfeed.dto.PostRequest;
import com.xowns.celfeed.dto.PostResponse;
import com.xowns.celfeed.dto.SliceDTO;
import com.xowns.celfeed.exception.ApiException;
import com.xowns.celfeed.exception.ErrorCode;
import com.xowns.celfeed.repository.MemberRepository;
import com.xowns.celfeed.repository.PostRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostService {

    private final PostRepository postRepository;
    private final MemberRepository memberRepository;

    @Transactional
    public Long write(Long loginId, @Valid PostRequest postRequest) {
        Member loginMember = findMember(loginId);
        Post savedPost = postRepository.save(Post.create(loginMember, postRequest.getContent()));
        return savedPost.getId();
    }

    public PostResponse findOne(Long postId) {
        Post findPost = postRepository.findById(postId, false)
                .orElseThrow(() -> new ApiException(ErrorCode.POST_NOT_FOUND));

        return PostResponse.of(findPost);
    }

    @Transactional
    public void updatePost(Long loginId, Long postId, PostRequest postRequest) {
        Member loginMember = findMember(loginId);
        Post findPost = postRepository.findByIdAndMember(postId, loginMember)
                .orElseThrow(() -> new ApiException(ErrorCode.POST_NOT_FOUND));

        findPost.updateContent(postRequest.getContent());
    }

    @Transactional
    public void deletePost(Long loginId, Long postId) {
        Member loginMember = findMember(loginId);
        Post findPost = postRepository.findByIdAndMember(postId, loginMember)
                .orElseThrow(() -> new ApiException(ErrorCode.POST_NOT_FOUND));

        findPost.deletePost();
    }

    public SliceDTO<PostResponse> findAll(Long memberId, Pageable pageable) {
        Member member = findMember(memberId);

        if (pageable.getSort().isUnsorted()) {
            pageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                    Sort.by("createdAt").descending());
        }

        Slice<PostResponse> posts = postRepository.findAllByMember(member, false, pageable)
                .map(PostResponse::of);

        return SliceDTO.of(posts);
    }

    // ====
    private Member findMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new ApiException(ErrorCode.MEMBER_NOT_FOUND));
    }
}

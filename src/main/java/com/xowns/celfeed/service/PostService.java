package com.xowns.celfeed.service;

import com.xowns.celfeed.domain.Like;
import com.xowns.celfeed.domain.Member;
import com.xowns.celfeed.domain.Post;
import com.xowns.celfeed.dto.post.PostDetailResponse;
import com.xowns.celfeed.dto.post.PostRequest;
import com.xowns.celfeed.dto.post.PostResponse;
import com.xowns.celfeed.dto.SliceDTO;
import com.xowns.celfeed.exception.ApiException;
import com.xowns.celfeed.exception.ErrorCode;
import com.xowns.celfeed.repository.LikeRepository;
import com.xowns.celfeed.repository.MemberRepository;
import com.xowns.celfeed.repository.PostQueryRepository;
import com.xowns.celfeed.repository.PostRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostService {

    private final PostRepository postRepository;
    private final PostQueryRepository postQueryRepository;
    private final MemberRepository memberRepository;
    private final LikeRepository likeRepository;

    @Transactional
    public Long write(Long loginId, PostRequest postRequest) {
        Member loginMember = getMemberOrThrow(loginId);
        Post savedPost = postRepository.save(Post.create(loginMember, postRequest.getContent()));
        return savedPost.getId();
    }

    public PostResponse findOne(Long postId) {
        Post findPost = getPostOrThrow(postId);
        return PostResponse.of(findPost);
    }

    public PostDetailResponse findOneDetail(Long loginId, Long postId) {
        return postQueryRepository.findByDetail(loginId, postId, false)
                .orElseThrow(() -> new ApiException(ErrorCode.POST_NOT_FOUND));
    }

    @Transactional
    public void updatePost(Long loginId, Long postId, PostRequest postRequest) {
        Member loginMember = getMemberOrThrow(loginId);
        Post findPost = getPostByMemberOrThrow(postId, loginMember);

        findPost.updateContent(postRequest.getContent());
    }

    @Transactional
    public void deletePost(Long loginId, Long postId) {
        Member loginMember = getMemberOrThrow(loginId);
        Post findPost = getPostByMemberOrThrow(postId, loginMember);

        findPost.deletePost();
    }

    public SliceDTO<PostResponse> findAll(Long memberId, Pageable pageable) {
        Member member = getMemberOrThrow(memberId);

        if (pageable.getSort().isUnsorted()) {
            pageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                    Sort.by("createdAt").descending());
        }

        Slice<PostResponse> posts = postRepository.findAllByMember(member, false, pageable)
                .map(PostResponse::of);

        return SliceDTO.of(posts);
    }

    @Transactional
    public Long likePost(Long loginId, Long postId) {
        Member member = getMemberOrThrow(loginId);
        Post findPost = getPostOrThrow(postId);

        Optional<Like> optionalLike = likeRepository.findByPostAndMember(findPost, member);
        if (optionalLike.isPresent()) {
            return optionalLike.get().getId();
        }

        Like savedLike = likeRepository.save(Like.create(findPost, member));
        return savedLike.getId();
    }

    @Transactional
    public void unlikePost(Long loginId, Long postId) {
        Member member = getMemberOrThrow(loginId);
        Post findPost = getPostOrThrow(postId);

        likeRepository.findByPostAndMember(findPost, member)
                .ifPresent(likeRepository::delete);
    }

    // ====
    private Member getMemberOrThrow(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new ApiException(ErrorCode.MEMBER_NOT_FOUND));
    }

    private Post getPostOrThrow(Long postId) {
        return postRepository.findById(postId, false)
                .orElseThrow(() -> new ApiException(ErrorCode.POST_NOT_FOUND));
    }

    private Post getPostByMemberOrThrow(Long postId, Member loginMember) {
        return postRepository.findByIdAndMember(postId, loginMember)
                .orElseThrow(() -> new ApiException(ErrorCode.POST_NOT_FOUND));
    }
}

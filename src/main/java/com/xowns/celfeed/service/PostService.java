package com.xowns.celfeed.service;

import com.xowns.celfeed.domain.*;
import com.xowns.celfeed.dto.NotificationBulkDTO;
import com.xowns.celfeed.dto.post.PostDetailResponse;
import com.xowns.celfeed.dto.post.PostRequest;
import com.xowns.celfeed.dto.post.PostResponse;
import com.xowns.celfeed.dto.SliceDTO;
import com.xowns.celfeed.exception.ApiException;
import com.xowns.celfeed.exception.ErrorCode;
import com.xowns.celfeed.repository.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostService {

    private final PostRepository postRepository;
    private final PostQueryRepository postQueryRepository;
    private final LikeRepository likeRepository;

    private final MemberRepository memberRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationBulkRepository notificationBulkRepository;
    private final FollowRepository followRepository;

    @Transactional
    public Long write(Long loginId, PostRequest postRequest) {
        Member loginMember = getMemberOrThrow(loginId);
        Post savedPost = postRepository.save(Post.create(loginMember, postRequest.getContent()));

        log.info("==== 알림 생성 ====");
        Member postMember = savedPost.getMember();
        List<Follow> followers = followRepository.findByToMember(postMember);

        List<NotificationBulkDTO> bulkList = followers.stream()
                .map(follower ->
                        new NotificationBulkDTO(
                                follower.getFromMember().getId(),
                                postMember.getId(),
                                NotificationType.WRITE_POST.name(),
                                NotificationTargetType.POST.name(),
                                savedPost.getId()
                        )
                )
                .toList();
        notificationBulkRepository.batchInsert(bulkList);

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

        log.info("==== 알림 생성 ====");
        Notification notification = Notification.create(findPost.getMember(), member, NotificationType.LIKE_POST, NotificationTargetType.POST, findPost.getId());
        notificationRepository.save(notification);

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

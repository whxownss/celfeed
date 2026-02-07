package com.xowns.celfeed.service.basic;

import com.xowns.celfeed.common.consts.KafkaTopicConst;
import com.xowns.celfeed.domain.basic.Like;
import com.xowns.celfeed.domain.basic.Member;
import com.xowns.celfeed.domain.basic.Post;
import com.xowns.celfeed.dto.post.PostDetailResponse;
import com.xowns.celfeed.dto.post.PostRequest;
import com.xowns.celfeed.dto.post.PostResponse;
import com.xowns.celfeed.dto.SliceDTO;
import com.xowns.celfeed.exception.ApiException;
import com.xowns.celfeed.exception.ErrorCode;
import com.xowns.celfeed.repository.basic.LikeRepository;
import com.xowns.celfeed.repository.basic.MemberRepository;
import com.xowns.celfeed.repository.basic.PostQueryRepository;
import com.xowns.celfeed.repository.basic.PostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostService {

    private final PostRepository postRepository;
    private final PostQueryRepository postQueryRepository;
    private final LikeRepository likeRepository;

    private final MemberRepository memberRepository;
    //private final NotificationSender notificationSender;
    private final KafkaTemplate<String, Long> kafkaTemplate;

    @Transactional
    public Long write(Long loginId, PostRequest postRequest) {
        Member loginMember = getMemberOrThrow(loginId);
        Post savedPost = postRepository.save(Post.create(loginMember, postRequest.getContent()));

        //notificationSender.sendWritePost(savedPost.getId());
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        kafkaTemplate.send(KafkaTopicConst.WRITE_POST, savedPost.getId());
                    }
                }
        );

        return savedPost.getId();
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

        Slice<PostResponse> posts = postRepository.findByMember(member, false, applyDefaultSort(pageable))
                .map(PostResponse::of);

        return SliceDTO.of(posts);
    }

    private Pageable applyDefaultSort(Pageable pageable) {
        return pageable.getSort().isSorted()
                ? pageable
                : PageRequest.of(
                    pageable.getPageNumber(),
                    pageable.getPageSize(),
                    Sort.by("createdAt").descending()
                );
    }

    @Transactional
    public Long likePost(Long loginId, Long postId) {
        Member member = getMemberOrThrow(loginId);
        Post findPost = getPostOrThrow(postId);

         return likeRepository.findByPostAndMember(findPost, member)
                 .map(Like::getId)
                 .orElseGet(() -> {
                     Like savedLike = likeRepository.save(Like.create(findPost, member));

                     //notificationSender.sendLikePost(savedLike.getId());
                     TransactionSynchronizationManager.registerSynchronization(
                             new TransactionSynchronization() {
                                 @Override
                                 public void afterCommit() {
                                     kafkaTemplate.send(KafkaTopicConst.LIKE_POST, savedLike.getId());
                                 }
                             }
                     );

                     return savedLike.getId();
                 });
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
        return postRepository.findByIdAndIsDeleted(postId, false)
                .orElseThrow(() -> new ApiException(ErrorCode.POST_NOT_FOUND));
    }

    private Post getPostByMemberOrThrow(Long postId, Member loginMember) {
        return postRepository.findByIdAndMember(postId, loginMember)
                .orElseThrow(() -> new ApiException(ErrorCode.POST_NOT_FOUND));
    }
}

package com.xowns.celfeed.repository.basic;

import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.xowns.celfeed.domain.basic.QLike;
import com.xowns.celfeed.dto.post.PostDetailResponse;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

import java.util.Optional;

import static com.xowns.celfeed.domain.basic.QLike.*;
import static com.xowns.celfeed.domain.basic.QMember.*;
import static com.xowns.celfeed.domain.basic.QPost.*;

@Repository
public class PostQueryRepository {

    private final JPAQueryFactory queryFactory;

    public PostQueryRepository(EntityManager em) {
        this.queryFactory = new JPAQueryFactory(em);
    }

    /**
     * [JPQL]
     * "select new com.xowns.celfeed.dto.post.PostDetailDTO(p.id, p.content, m.nickname, count(l), " +
     *                     " case when l2.id is not null then true else false end, " +
     *                     " p.createdAt, p.updatedAt) " +
     *             "  from Post p " +
     *             "  join p.member m " +
     *             "  left join Like l on l.post = p " +
     *             "  left join Like l2 on l2.post = p and l2.member = :member" +
     *             " where p.id = :postId " +
     *             "   and p.isDeleted = :isDeleted " +
     *             " group by p.id, p.content, m.nickname, p.createdAt, p.updatedAt"
     */
    public Optional<PostDetailResponse> findByDetail(Long loginId, Long postId, boolean isDeleted) {
        QLike like2 = new QLike("like2");
        PostDetailResponse postDetail = queryFactory
                .select(Projections.constructor(PostDetailResponse.class,
                        post.id,
                        post.content,
                        member.nickname,
                        like.count(),
                        like2.member.id.when(loginId).then(true).otherwise(false),
                        post.createdAt,
                        post.updatedAt
                ))
                .from(post)
                .join(post.member, member)
                .leftJoin(like).on(post.eq(like.post))
                .leftJoin(like2).on(post.eq(like2.post).and(like2.member.id.eq(loginId)))
                .where(
                        post.id.eq(postId),
                        post.isDeleted.eq(isDeleted)
                )
                .groupBy(post.id, post.content, member.nickname, post.createdAt, post.updatedAt)
                .fetchOne();
        return Optional.ofNullable(postDetail);
    }
}

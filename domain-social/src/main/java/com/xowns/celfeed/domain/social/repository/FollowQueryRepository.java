package com.xowns.celfeed.domain.social.repository;

import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.xowns.celfeed.domain.social.dto.FollowerDTO;
import com.xowns.celfeed.domain.social.entity.Member;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

import java.util.List;

import static com.xowns.celfeed.domain.social.entity.QFollow.*;

@Repository
public class FollowQueryRepository {

    private final JPAQueryFactory queryFactory;

    public FollowQueryRepository(EntityManager em) {
        this.queryFactory = new JPAQueryFactory(em);
    }

    public Long findBoundaryFollowerId(Long toId, Long cursorFromId, int batchSize) {
        return queryFactory
                .select(follow.fromMember.id)
                .from(follow)
                .where(
                        follow.toMember.id.eq(toId),
                        fromIdGt(cursorFromId)
                )
                .orderBy(follow.fromMember.id.asc())
                .offset(batchSize - 1)
                .fetchFirst();
    }

    private BooleanExpression fromIdGt(Long fromId) {
        return fromId != null ? follow.fromMember.id.gt(fromId) : null;
    }

    public List<FollowerDTO> findFollowerIds(Long toId, Long startFromId, Long endFromId) {
        return queryFactory
                .select(Projections.constructor(FollowerDTO.class, follow.id, follow.fromMember.id))
                .from(follow)
                .where(
                        follow.toMember.id.eq(toId),
                        fromIdGt(startFromId),
                        fromIdLoe(endFromId)
                )
                .orderBy(follow.fromMember.id.asc())
                .fetch();
    }

    private BooleanExpression fromIdLoe(Long fromId) {
        return fromId != null ? follow.fromMember.id.loe(fromId) : null;
    }

    public List<FollowerDTO> findFollowerIdsByCursor(Member toMember, Long cursorId, long count) {
        return queryFactory
                .select(Projections.constructor(FollowerDTO.class, follow.id, follow.fromMember.id))
                .from(follow)
                .where(
                        follow.toMember.eq(toMember),
                        idGt(cursorId)
                )
                .orderBy(follow.id.asc())
                .limit(count)
                .fetch();
    }

    private BooleanExpression idGt(Long cursorId) {
        return cursorId != null ? follow.id.gt(cursorId) : null;
    }
}

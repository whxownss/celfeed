package com.xowns.celfeed.repository.basic;

import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.xowns.celfeed.domain.basic.Member;
import com.xowns.celfeed.domain.basic.QFollow;
import com.xowns.celfeed.dto.follow.FollowerDTO;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

import java.util.List;

import static com.xowns.celfeed.domain.basic.QFollow.*;

@Repository
public class FollowQueryRepository {

    private final JPAQueryFactory queryFactory;

    public FollowQueryRepository(EntityManager em) {
        this.queryFactory = new JPAQueryFactory(em);
    }

    public List<FollowerDTO> findFollowerIdsByCursor(Member toMember, Long cursorId, long count) {
        return queryFactory
                .select(Projections.constructor(FollowerDTO.class,follow.id, follow.fromMember.id))
                .from(follow)
                .where(
                        follow.toMember.eq(toMember),
                        idGreaterThan(cursorId)
                )
                .orderBy(follow.id.asc())
                .limit(count)
                .fetch();
    }

    private BooleanExpression idGreaterThan(Long cursorId) {
        return cursorId != null ? follow.id.gt(cursorId) : null;
    }
}

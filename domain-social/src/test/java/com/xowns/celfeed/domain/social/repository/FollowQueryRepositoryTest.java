package com.xowns.celfeed.domain.social.repository;

import com.xowns.celfeed.domain.social.dto.FollowerDTO;
import com.xowns.celfeed.domain.social.entity.Follow;
import com.xowns.celfeed.domain.social.entity.Member;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FollowQueryRepositoryTest extends SocialRepositoryTestSupport {

    @Autowired
    FollowQueryRepository followQueryRepository;

    /** celeb 을 팔로우하는 팔로워 count 명을 만들고, 팔로워 id 를 오름차순으로 반환 */
    private List<Long> followers(Member celeb, int count) {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Member fan = member();
            follow(fan, celeb);
            ids.add(fan.getId());
        }
        flushAndClear();
        return ids; // IDENTITY 라 생성 순서 = id 오름차순
    }

    /** fanout 구간 (start, end] — null 은 처음/끝까지 */
    record Segment(Long start, Long end) {
    }

    /** server-consumer WritePostListener#process 와 같은 방식으로 구간을 만든다 */
    private List<Segment> segments(Long celebId, int batchSize) {
        List<Segment> segments = new ArrayList<>();
        Long cursor = null;
        while (true) {
            Long boundary = followQueryRepository.findBoundaryFollowerId(celebId, cursor, batchSize);
            segments.add(new Segment(cursor, boundary));
            if (boundary == null) break;
            cursor = boundary;
        }
        return segments;
    }

    // ========================================================================================
    // findBoundaryFollowerId
    // ========================================================================================

    @Test
    @DisplayName("경계값은 커서 이후 batchSize 번째 팔로워 id 다")
    void boundaryIsNthFollowerAfterCursor() {
        Member celeb = member();
        List<Long> ids = followers(celeb, 7);

        assertThat(followQueryRepository.findBoundaryFollowerId(celeb.getId(), null, 3)).isEqualTo(ids.get(2));
        assertThat(followQueryRepository.findBoundaryFollowerId(celeb.getId(), ids.get(2), 3)).isEqualTo(ids.get(5));
    }

    @Test
    @DisplayName("커서 이후 남은 팔로워가 batchSize 보다 적으면 null 이다 (마지막 구간)")
    void boundaryIsNullWhenNotEnough() {
        Member celeb = member();
        List<Long> ids = followers(celeb, 7);

        assertThat(followQueryRepository.findBoundaryFollowerId(celeb.getId(), ids.get(5), 3)).isNull();
    }

    @Test
    @DisplayName("다른 회원의 팔로워는 경계값 계산에 포함되지 않는다")
    void boundaryIgnoresOtherCelebFollowers() {
        Member celeb = member();
        Member otherCeleb = member();
        List<Long> ids = followers(celeb, 2);
        followers(otherCeleb, 5);

        assertThat(followQueryRepository.findBoundaryFollowerId(celeb.getId(), null, 2)).isEqualTo(ids.get(1));
        assertThat(followQueryRepository.findBoundaryFollowerId(celeb.getId(), null, 3)).isNull();
    }

    // ========================================================================================
    // findFollowerIds
    // ========================================================================================

    @Test
    @DisplayName("팔로워 구간 조회는 (start, end] 범위를 id 오름차순으로 반환한다")
    void followerIdsInRange() {
        Member celeb = member();
        List<Long> ids = followers(celeb, 5);

        assertThat(followQueryRepository.findFollowerIds(celeb.getId(), ids.get(0), ids.get(3)))
                .containsExactly(ids.get(1), ids.get(2), ids.get(3));
    }

    @Test
    @DisplayName("start 가 null 이면 처음부터, end 가 null 이면 끝까지 조회한다")
    void followerIdsOpenRange() {
        Member celeb = member();
        List<Long> ids = followers(celeb, 4);

        assertThat(followQueryRepository.findFollowerIds(celeb.getId(), null, ids.get(1)))
                .containsExactly(ids.get(0), ids.get(1));
        assertThat(followQueryRepository.findFollowerIds(celeb.getId(), ids.get(1), null))
                .containsExactly(ids.get(2), ids.get(3));
        assertThat(followQueryRepository.findFollowerIds(celeb.getId(), null, null))
                .containsExactlyElementsOf(ids);
    }

    // ========================================================================================
    // fanout 구간 분할 (경계값 + 구간 조회 조합)
    // ========================================================================================

    @ParameterizedTest(name = "팔로워 {0}명, batchSize {1} → 구간 {2}개")
    @CsvSource({
            "0, 3, 1",   // 팔로워 없음 → 빈 구간 1개
            "2, 3, 1",   // batchSize 미만 → 전체 1구간
            "7, 3, 3",   // 3 + 3 + 1
            "6, 3, 3",   // 3 + 3 + 빈 구간 (아래 테스트 참고)
            "1, 1, 2"    // 1 + 빈 구간
    })
    @DisplayName("구간을 나눠 조회하면 모든 팔로워가 빠짐없이, 겹치지 않고 한 번씩 포함된다")
    void segmentsCoverAllFollowersExactlyOnce(int followerCount, int batchSize, int expectedSegments) {
        Member celeb = member();
        List<Long> ids = followers(celeb, followerCount);

        List<Segment> segments = segments(celeb.getId(), batchSize);

        List<Long> collected = new ArrayList<>();
        for (Segment segment : segments) {
            List<Long> part = followQueryRepository.findFollowerIds(celeb.getId(), segment.start(), segment.end());
            assertThat(part).hasSizeLessThanOrEqualTo(batchSize);
            collected.addAll(part);
        }

        assertThat(segments).hasSize(expectedSegments);
        assertThat(collected).containsExactlyElementsOf(ids); // 순서대로, 중복 없이 전부
    }

    @Test
    @DisplayName("[현재 동작] 팔로워 수가 batchSize 의 배수면 마지막에 팔로워가 없는 빈 구간이 하나 더 생긴다")
    void exactMultipleProducesEmptyLastSegment() {
        Member celeb = member();
        List<Long> ids = followers(celeb, 6);

        List<Segment> segments = segments(celeb.getId(), 3);

        assertThat(segments).containsExactly(
                new Segment(null, ids.get(2)),
                new Segment(ids.get(2), ids.get(5)),
                new Segment(ids.get(5), null)
        );
        assertThat(followQueryRepository.findFollowerIds(celeb.getId(), ids.get(5), null)).isEmpty();
    }

    /**
     * 현재 동작 기록 (중요) : 첫 구간은 start 가, 마지막 구간은 end 가 항상 null 이다.
     * server-consumer WritePostFanoutListener#parse 는 startFollowerId / endFollowerId 가 null 이면
     * "필수 값 누락" 으로 NonRetryable 처리하므로, 첫/마지막 구간 메시지가 처리되지 않을 수 있다. (consumer 테스트에서 확인 예정)
     */
    @Test
    @DisplayName("[현재 동작] 첫 구간의 start 와 마지막 구간의 end 는 항상 null 이다")
    void firstStartAndLastEndAreNull() {
        Member celeb = member();
        followers(celeb, 7);

        List<Segment> segments = segments(celeb.getId(), 3);

        assertThat(segments.getFirst().start()).isNull();
        assertThat(segments.getLast().end()).isNull();
    }

    // ========================================================================================
    // findFollowerIdsByCursor
    // ========================================================================================

    @Test
    @DisplayName("팔로우 id 커서 기반으로 (팔로우 id, 팔로워 id)를 count 개씩 조회한다")
    void followerIdsByCursor() {
        Member celeb = member();
        List<Long> followerIds = new ArrayList<>();
        List<Long> followIds = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Member fan = member();
            Follow follow = follow(fan, celeb);
            followerIds.add(fan.getId());
            followIds.add(follow.getId());
        }
        flushAndClear();
        Member managedCeleb = em.find(Member.class, celeb.getId());

        List<FollowerDTO> first = followQueryRepository.findFollowerIdsByCursor(managedCeleb, null, 2);
        List<FollowerDTO> second = followQueryRepository.findFollowerIdsByCursor(managedCeleb, first.getLast().getId(), 2);

        assertThat(first).extracting(FollowerDTO::getId).containsExactly(followIds.get(0), followIds.get(1));
        assertThat(first).extracting(FollowerDTO::getFollowerId).containsExactly(followerIds.get(0), followerIds.get(1));
        assertThat(second).extracting(FollowerDTO::getId).containsExactly(followIds.get(2));
    }
}

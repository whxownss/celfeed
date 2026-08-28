# [Querydsl] Cursor-based Pagination

<br>

## 1. 개요

> 게시글을 작성하면 팔로워에게 전송할 알림 데이터 저장을 위해 작성자의 팔로워 목록을 조회합니다.
> 
> 이때 팔로워 수가 많은 작성자의 경우, OOM 및 INSERT 시 병목 등의 이유로 전체를 한 번에 조회하지 않고 일정 크기씩 나누어 조회하는 페이징 쿼리를 사용합니다.
> 
> 페이징 방식에 따라 성능 차이가 발생할 수 있기 때문에 **오프셋 기반 페이징**과 **커서 기반 페이징**을 비교하여 더 적절한 방식을 알아보겠습니다.

<br>

## 2. Offset-based Pagination

> 기존에 사용 중인 오프셋 기반 페이징 방식을 살펴보겠습니다.
> 

<br>

**✅ 애플리케이션 코드**
```java
Member postWriter = post.getMember();
int pageNumber = 0;

while (true) {
    PageRequest pageRequest = PageRequest.of(pageNumber, BATCH_SIZE, Sort.by("id").ascending()); // [1]
			  
    List<Long> followerIds = followRepository.findFollowerIdsByOffset(postWriter, pageRequest); // [2]
		    
    if (followerIds.isEmpty()) break;

    // [3]
    writePostNotiKafkaTemplate.send(
            KafkaTopicConst.NOTI_BATCH,
            new WritePostNotiMessage(followerIds, postWriter.getId(), postId, ...)
    );

    pageNumber++;
}
```
**[1]** 페이징과 정렬 정보를 담는 Spring Data의 `PageRequest` 객체로, `BATCH_SIZE` 값이 count가 되고 `pageNumber * BATCH_SIZE` 값이 offset이 됩니다.

**[2]** `JpaRepository`를 통해 조회했으며, 자세한 내용은 바로 다음에 확인할 수 있습니다.

**[3]** 조회한 팔로워를 대상으로 알림 데이터를 생성하도록 `KafkaTemplate`으로 메시지를 전송했습니다.

<br>

**✅ findFollowerIdsByOffset**
```java
public interface FollowRepository extends JpaRepository<Follow, Long> {
    ...
    @Query("select f.fromMember.id from Follow f where f.toMember = :toMember")
    List<Long> findFollowerIdsByOffset(@Param("toMember") Member toMember,
                                                          Pageable pageable);
}
```
- `toMember`를 팔로우하는 회원들의 id 목록을 페이징하여 조회합니다.
- Spring Data JPA는 파라미터에 `Pageable`이 존재하면 자동으로 limit/offset을 적용합니다.
- `PageRequest`는 `Pageable` 인터페이스의 구현 클래스입니다.

<br>

**✅ SQL**
```sql
select from_id
from follow
where to_id = 2
order by id
limit {offset}, {count};
```
- 실제 실행되는 쿼리입니다.
- 문제점
    - 앞 페이지의 데이터를 전부 읽는 오프셋 페이징 특성상 offset 값이 클수록 **쿼리 속도가 느려지는 문제**가 있습니다.
        
        ![issue4-1-1](/docs/img/issue4-1-1.png)
        - 맨 앞의 2명을 조회할 때는 `0.016s`가 걸렸습니다.
        - 반면, 100만 명 중 맨 뒤의 2명을 조회할 때는 `3.157s`가 걸렸습니다.
    - 첫 번째 페이지(1, 2)를 읽고 두 번째 페이지(3, 4)를 읽기 전에, 첫 번째 페이지에 있는 데이터(2)가 삭제되면 그 다음 페이지(4, 5)를 읽을 때 **데이터 누락**(3)이 발생합니다.

<br>

➡️ **오프셋 기반 페이징** 방식은 페이지 번호가 커질수록 **쿼리 성능이 저하**되고, 동시성 문제로 인해 **데이터가 누락**될 수 있다는 단점이 있습니다.

<br>

## 3. Cursor-based Pagination

> 앞서 살펴본 오프셋 페이징의 단점을 해결할 수 있는 커서 기반 페이징 방식을 살펴보겠습니다.

<br>

**✅ 애플리케이션 코드**
```java
Member postWriter = post.getMember();
Long cursorId = null;

while (true) {  
    List<FollowerDTO> followers = followQueryRepository.findFollowerIdsByCursor(postWriter, cursorId, BATCH_SIZE); // [1]
    
    if (followers.isEmpty()) break;
    
    List<Long> followerIds = followers.stream()
                                      .map(FollowerDTO::getFollowerId)
                                      .collect(Collectors.toList()); // [2]
    
    writePostNotiKafkaTemplate.send(
            KafkaTopicConst.NOTI_BATCH,
            new WritePostNotiMessage(followerIds, postWriter.getId(), postId, ...)
    );
    
    cursorId = followers.getLast().getId(); // [3]
}
```
**[1]** Follow 테이블의 PK를 담는 `id` 필드와 from_id 컬럼(팔로워 id)을 담는 `followerId` 필드로 구성된 DTO 목록을 조회합니다.

**[2]** 조회한 DTO 목록에서 `followerId`만 따로 추출합니다.

**[3]** 마지막 요소의 `id`를 `cursorId`로 설정합니다.

<br>

✅ **findFollowerIdsByCursor**
```java
@Repository
public class FollowQueryRepository {

    private final JPAQueryFactory queryFactory; // [1]

    public FollowQueryRepository(EntityManager em) {
        this.queryFactory = new JPAQueryFactory(em);
    }

    public List<FollowerDTO> findFollowerIdsByCursor(Member toMember, Long cursorId, long count) {
        return queryFactory
                .select(Projections.constructor(
                        FollowerDTO.class,
                        follow.id, follow.fromMember.id
                ))
                .from(follow)
                .where(
                        follow.toMember.eq(toMember),
                        idGreaterThan(cursorId) // [2]
                )
                .orderBy(follow.id.asc())
                .limit(count)
                .fetch();
    }
    
    private BooleanExpression idGreaterThan(Long cursorId) {
        return cursorId != null ? follow.id.gt(cursorId) : null; // [2]
    }
}
```
**[1]** 첫 번째 페이지를 조회할 때 쿼리와 이후 페이지들을 조회할 때 쿼리가 다르기 때문에 동적 쿼리 작성에 유용한 Querydsl을 사용했습니다.

**[2]** where 조건에서 null은 무시되기 때문에 `cursorId` 값에 따라 동적 쿼리가 가능합니다.

<br>

**✅ SQL**
```sql
select id, from_id
from follow
where to_id = 2
    and id > {cursorId} -- 첫 번째 페이지에서는 생략
order by id
limit {count};
```

- 인덱스 추가 전 실행 계획
    
    ![issue4-1-2](/docs/img/issue4-1-2.png)    
    - **type 컬럼**의 `index_merge`는 여러 인덱스를 이용해 각각의 검색 결과를 만들어낸 후 그 결과를 병합해서 처리하는 방식으로, `range` 접근 방법보다 효율성이 떨어집니다.
    - **Extra 컬럼**
        - `Using intersect`는 각각의 인덱스를 사용할 수 있는 조건이 AND로 연결된 경우 각 처리 결과에서 교집합을 추출해낼 때 표시됩니다.
        - `Using where`은 스토리지 엔진이 읽은 레코드를 MySQL 엔진에서 추가로 필터링한 경우에 표시됩니다.
        - `Using filesort`는 order by를 처리할 때 인덱스를 사용하지 못해서 조회된 레코드를 정렬용 메모리 버퍼에 복사해 정렬을 수행하게 된다는 것을 의미합니다.
- 인덱스 추가 후 실행 계획
    
    ![issue4-1-3](/docs/img/issue4-1-3.png)     
    - `idx_toid_id(to_id, id)` 인덱스를 추가함으로써 **type 컬럼**이 인덱스 레인지 스캔을 의미하는 `range`로 변경되었습니다.
    - **Extra 컬럼**의 `Using index condition`은 인덱스를 스캔하는 단계에서 where 조건을 필터링할 때 표시됩니다.
- 오프셋 페이징과 달리, 필요한 데이터만 읽기 때문에 맨 앞의 2명을 조회할 때와 100만 명 중 맨 뒤의 2명을 조회할 때 모두 `0.015s`로 짧은 시간이 걸렸습니다.
    
    ![issue4-1-4](/docs/img/issue4-1-4.png)
    
<br>

➡️ **커서 기반 페이징** 방식에서는 페이지 번호가 커져도 더이상 **쿼리 성능에 저하가 없고**, 조회 도중에 데이터가 삭제되어도 **데이터 누락이 발생하지 않습니다.**

<br>

## 4. 성능 비교

> 팔로워 100만 명을 10만 명 단위로 나누어 모두 조회하는 상황에서, 페이징 쿼리 방식별 응답 시간을 비교해 보겠습니다.

<br>

**✅ 테스트 코드**
```java
public void testPaging() {
    List<Long> executionTimes = new ArrayList<>();
    
    while (true) {
        long start = System.currentTimeMillis();
        
        // 오프셋 페이징 or 커서 페이징 로직 ...
        
        long end = System.currentTimeMillis();
        executionTimes.add(end - start);
    }
    
    log.info("executionTimes={}", executionTimes);
}
```
- 각 페이지마다 수행 시간 비교를 위해 `executionTimes` 변수에 `end - start` 값을 저장했습니다.

<br>

**✅ 오프셋 기반 페이징**

![issue4-1-5](/docs/img/issue4-1-5.png)

| 페이지 번호 | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 | 10 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 수행 시간 (ms) | 203 | 417 | 669 | 853 | 1,153 | 1,430 | 1,911 | 2,308 | 2,650 | 3,100 |
- 전체 응답 시간은 `17,560ms` 소요되었습니다.
- 뒷 페이지로 갈수록 수행 시간이 점점 느려지는 것을 확인할 수 있습니다.

<br>

**✅ 커서 기반 페이징**

![issue4-1-6](/docs/img/issue4-1-6.png)

| 페이지 번호 | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 | 10 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 수행 시간 (ms) | 269 | 126 | 107 | 118 | 120 | 182 | 134 | 112 | 161 | 267 |
- 오프셋 페이징보다 **약 10배 이상** 빠른 `1,620ms` 소요되었습니다.
- 페이지마다 큰 차이 없이 비슷한 수행 시간이 걸리는 것을 확인할 수 있습니다.

<br>

➡️ 100만 명의 팔로워를 10만 명 단위로 조회한 결과, 오프셋 페이징 방식 대비 **커서 페이징** 방식에서 **약 90%의 응답 시간 향상**이 확인되었습니다.

<br><br>

[뒤로가기](https://github.com/whxownss/celfeed#4-%EC%95%8C%EB%A6%BC-%EC%84%9C%EB%B2%84%EC%9D%98-%EB%B0%9C%EC%A0%84)

# ERD 설계 및 근거

<img width="1177" height="720" alt="image" src="https://github.com/user-attachments/assets/c94335b8-6815-4c54-8533-d343f6aebf62" />


<details>
  <summary>공통</summary>
  
  * `member`, `follow`, `post`, `likes` 테이블의 PK인 `id` 컬럼은 전부 `AUTO_INCREMENT`로 설정했습니다.
  * 모든 테이블의 모든 컬럼에는 `NOT NULL` 제약조건을 설정했습니다.

</details>

<details>
  <summary>member</summary>

  * `role` : `CELEB`(셀럽)과 `FAN`(팬) 두 가지로 구분됩니다.
  * `uk_member_email` : `email` 컬럼의 중복 방지를 위해 `UNIQUE` 제약조건을 설정했습니다.
  * `uk_member_nickname` : `nickname` 컬럼의 중복 방지를 위해 `UNIQUE` 제약조건을 설정했습니다.

</details>

<details>
  <summary>follow</summary>
  
  * `fk_follow_fromid` : `from_id` 컬럼은 `member` 테이블의 `id` 컬럼을 참조하는 외래키입니다.
  * `fk_follow_toid` : `to_id` 컬럼은 `member` 테이블의 `id` 컬럼을 참조하는 외래키(`to_id`)입니다.
  * `uk_follow_fromid_toid`
    * `from_id`(팔로우를 건 회원)와 `to_id`(팔로우를 받은 회원) 쌍에 `UNIQUE` 제약조건을 설정하여 같은 회원끼리의 팔로우 관계가 한 번만 생성되도록 했습니다.
    * (`from_id`, `to_id`) 순서로 설정한 이유
      * 우선, 프로젝트 특성상 셀럽 회원보다 팬 회원이 더욱 많습니다.
      * 그리고 팬들은 주로 자신의 팔로잉 목록에서 팔로우를 한 셀럽을 찾는 경우가 잦습니다.
      * 즉, 특정 회원이 누구를 팔로우하는지(`from_id -> to_id`) 검색하는 경우가 많기 때문입니다.

</details>

<details>
  <summary>post</summary>
  
  * `fk_post_memberid` : `member_id` 컬럼은 `member` 테이블의 `id` 컬럼을 참조하는 외래키입니다.
  * `idx_post_memberid_isdeleted_createdat`
    * 게시글을 검색할 때, 특정 셀럽(`member_id`)의 삭제되지 않은(`is_deleted`) 게시글을 최신순(`created_at`)으로 조회합니다.
    * 동등 비교와 정렬을 모두 인덱스에서 해결하기 위해 (`member_id`, `is_deleted`, `created_at DESC`) 순서로 복합 인덱스를 설정했습니다.

</details>

<details>
  <summary>likes</summary>
  
  * `fk_likes_postid` : `post_id` 컬럼은 `post` 테이블의 `id` 컬럼을 참조하는 외래키입니다.
  * `fk_likes_memberid` : `member_id` 컬럼은 `member` 테이블의 `id` 컬럼을 참조하는 외래키입니다.
  * `uk_postid_memberid`
    * `post_id`와 `member_id` 쌍에 `UNIQUE` 제약조건을 설정하여 한 게시글에 좋아요를 한 번만 누를 수 있도록 했습니다.
    * 또한, 특정 게시글(`post_id`)에 좋아요를 누른 회원(`member_id`) 목록을 조회하는 경우가 잦기 때문에 (`post_id`, `member_id`) 순서로 키를 설정했습니다.
  * `idx_likes_memberid_createdat_postid`
    * 팬(`member_id`)은 최근에(`created_at`) 좋아요를 누른 셀럽의 게시글(`post_id`) 목록을 조회할 수 있습니다.
    * 동등 비교와 정렬을 인덱스에서 한 번에 처리하고, 커버링 인덱스까지 활용하기 위해 (`member_id`, `created_at DESC`, `post_id`) 순서로 복합 인덱스를 설정했습니다.

</details>

<details>
  <summary>notification</summary>
  
  * 대량의 알림 데이터를 효율적으로 관리하기 위해 `파티셔닝`과 `샤딩`을 적용했습니다.
  * (`id`, `created_at`) : 파티션 테이블에 PK를 지정할 때, 반드시 파티션 키도 포함되어야 하기 때문에 복합 키로 구성했습니다.
  * `id` : 샤딩 처리된 분산 DB 환경에서 전역적으로 고유한 키 값을 생성하기 위해 `Snowflake ID`를 도입했습니다.
  * `idx_receiverid_createdat`
    * 알림을 조회할 때, 나(`receiver_id`)의 최근 30일(`created_at`) 알림을 조회합니다.
    * 동등 비교와 정렬을 모두 인덱스에서 해결하기 위해 (`receiver_id`, `created_at DESC`) 순서로 복합 인덱스를 설정했습니다.

</details>


<br><br>

[뒤로가기](https://github.com/whxownss/celfeed#3-erd)

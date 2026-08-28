# 알림 테이블 파티셔닝 : 데이터 조회 및 삭제 쿼리 속도 향상

## 1. 개요

> 알림 테이블은 서비스 특성상 다른 테이블에 비해 데이터가 매우 빠른 속도로 누적됩니다. 이로 인해 시간이 지날수록 **데이터베이스 성능 저하의 원인**이 될 수 있습니다.
> 
> 테이블이 커지면 데이터와 인덱스 페이지가 Buffer Pool에 충분히 적재되지 않아, 캐시 미스가 빈번히 발생합니다. 그 결과 디스크 I/O가 증가하고, **단순한 조회 쿼리조차 응답 시간이 점점 느려**집니다.
> 
> 또한 효율적인 데이터 관리를 위해 특정 기간이 지난 **데이터의 삭제 작업 역시 큰 부담**이 됩니다. 하나의 거대한 테이블에서 DELETE 작업을 수행할 경우, 각 row에 락이 걸려 작업 시간이 길어지고 그동안 다른 트랜잭션에도 영향을 미치게 됩니다.

<br>

## 2. 기존 구조의 문제

> 알림 서비스는 최근 30일까지의 알림만 제공합니다. **하나의 테이블로 모든 데이터를 관리**할 때 발생할 수 있는 문제를 자세히 살펴보겠습니다.
> 
> `notification` 테이블에는 총 500만 건의 데이터가 존재합니다. (11월 100만, 12월 200만, 1월 200만)
> 
> (모든 쿼리를 실행하기 전에 MySQL 서버를 재시작하여 Buffer Pool 초기화 후 실행했습니다.)

<br>

**✅ 최근 30일 데이터 조회**
```sql
select n.id, n.receiver_id, n.type, n.target_id, n.is_read, n.created_at, m.nickname
from notification n 
join member m on m.id = n.actor_id 
where n.receiver_id = 51 
    and n.created_at <= current_timestamp()
    and n.created_at >= curdate() - interval 29 day
order by n.created_at desc;
```
- id가 51인 회원의 알림 데이터는 2건이 있는데 `3.047s`가 소요되었습니다.
- 실행 계획
    
    ![issue5-0-1](/docs/img/issue5-0-1.png)
    
    - n(notification) 테이블을 중점적으로 보겠습니다.
    - **type 컬럼**
        - `ALL`은 풀 테이블 스캔으로 접근하는 것을 의미합니다.
        - where 절에 이용할 수 있는 적절한 인덱스가 없기 때문에 풀 테이블 스캔을 선택한 경우입니다.
    - **filtered 컬럼**
        - rows 컬럼(쿼리를 처리하기 위해 읽어야 하는 레코드 수의 예측치)에서 1.11%만 필터링된 결과로 남을 것으로 예측하고 있습니다.
    - **Extra 컬럼**
        - `Using where`은 스토리지 엔진이 읽은 레코드를 MySQL 엔진에서 추가로 필터링한 경우에 표시되며, filtered 컬럼값을 통해 Using where가 성능상 이슈가 있음을 알 수 있습니다.
        - `Using filesort`는 order by를 처리할 때 인덱스를 사용하지 못해서 조회된 레코드를 정렬용 메모리 버퍼에 복사해 정렬을 수행하게 된다는 것을 의미합니다.
- 실행 계획 분석을 통해 인덱스를 추가하면 어느 정도 성능 향상이 있을 것으로 예측할 수 있습니다.

<br>

**✅ 인덱스 추가**
```sql
create index idx_receiver_create on notification (receiver_id, created_at desc);
```
- 인덱스 추가 후 동일한 쿼리를 조회했을 때 `0.016s`로 **약 190배 빨라진** 것을 확인했습니다.
- 실행 계획
    
    ![issue5-0-2](/docs/img/issue5-0-2.png)
    
    - **type 컬럼**
        - `range`는 인덱스 레인지 스캔으로 접근하는 것을 의미합니다.
        - where 절에서 이용할 수 있는 인덱스를 추가해서 인덱스 레인지 스캔을 선택한 것입니다.
    - **key 컬럼**
        - 의도했던 `idx_receiver_create` 인덱스가 선택된 것을 확인할 수 있습니다.
    - **Extra 컬럼**
        - `Using index condition`은 인덱스를 스캔하는 단계에서 where 조건을 필터링할 때 표시됩니다.
- 인덱스가 과연 진정한 해결책일까?
    - 인덱스도 결국 절대적인 데이터 양이 많아질수록 느려집니다.
    - 또한 인덱스를 추가하더라도, 대용량 테이블에서 `delete`할 때 발생하는 비용과 락 문제까지는 해결할 수 없습니다.

<br>

**✅ 데이터 삭제**
```sql
delete
  from notification
 where created_at >= '2025-11-01'
   and created_at < '2025-12-01';
```
- 100만 건의 데이터가 있는 11월 데이터를 삭제하는 데 `22.469s`가 소요되었습니다.
- MySQL의 InnoDB 스토리지 엔진에서 `delete`는 조건에 맞는 각 row마다 다음과 같은 과정을 거칩니다.
    1. where 조건에 맞는 row를 찾습니다.
    2. 해당 row에 락을 걸어 다른 트랜잭션이 수정하지 못하도록 합니다.
    3. Undo/Redo 로그를 작성합니다.
    4. 삭제되는 row와 관련된 모든 인덱스 엔트리를 제거합니다.
    5. row를 삭제합니다.
- 인덱스를 사용해 삭제 대상 row를 빠르게 찾더라도, 이후 과정 때문에 결국 `delete` 작업이 느려집니다.

<br>

## 3. Partition 적용
> 파티션이란, **논리적으로는 하나의 테이블이지만 물리적으로 여러 개의 파티션**으로 나누어 데이터를 분산 저장하는 방식입니다.

<br>

**✅ Notification 테이블**
```sql
create table notification (
    id          bigint      not null auto_increment,
    created_at  datetime(6) not null,
    ...
    primary key (id, created_at)
)
partition by range (to_days(created_at)) (
    partition p202511 values less than (to_days('2025-12-01')),
    partition p202512 values less than (to_days('2026-01-01')),
    partition p202601 values less than (to_days('2026-02-01')),
    partition pmax    values less than maxvalue
);
```
- 파티션 테이블에서는 PK를 지정할 때, 반드시 파티션 키도 함께 포함되어야 하기 때문에 파티션 키로 사용되는 **created_at 컬럼을 id 컬럼과 함께 복합 키로 구성**했습니다.
- 파티션 적용 후 최근 30일 데이터를 조회했을 때는 `1.281s`가 소요되었습니다.
- 실행 계획
    
    ![issue5-0-3](/docs/img/issue5-0-3.png)
    
    - **partitions 컬럼**
        - 쿼리 수행 시 접근해야 할 것으로 판단되는 파티션이 표시됩니다.
        - **파티션 프루닝**이 적용되어 딱 필요한 파티션(p202601)만 접근하는 것을 확인할 수 있습니다.
- 앞서 파티션을 적용하기 전과 비슷한 상황으로, 인덱스를 추가해 볼 필요가 있습니다.

<br>

**✅ Notification 엔티티**

<aside>

- 파티션 적용을 위해 PK를 복합 키로 설계했으나, JPA에서는 복합 키와 `@GeneratedValue`를 함께 사용할 수 없는 제약이 있습니다.
- 이 문제를 해결하기 위해 복합 키 대신 [id 컬럼만 @Id로 매핑](/docs/issues/issue5-1.md)했습니다.
</aside>

<br>

**✅ 인덱스 추가**
```sql
create index idx_receiver_create on notification (receiver_id, created_at desc);
```
- 인덱스 추가 후 동일한 쿼리를 조회했을 때 `0.016s`로 **약 80배 빨라진** 것을 확인했습니다.
- 실행 계획
    
    ![issue5-0-4](/docs/img/issue5-0-4.png)
    
    - type 컬럼이 `range`(인덱스 레인지 스캔)로 표시되었습니다.

<br>

**✅ 데이터 삭제**
```sql
alter table notification drop partition p202511;
```
- 100만 건의 데이터가 있는 11월 파티션을 삭제하는 데 `0.046s`가 소요되었습니다.
- `delete`로 삭제한 경우(`22.469s`)에 비해 **약 488배 빠른** 성능 향상을 보였습니다.

<br>

## 4. 성능 비교

|  | 파티션 적용 전 | 파티션 적용 후 | 비교 |
| :--- | ---: | ---: | ---: |
| 최근 30일 데이터 조회 | 3,047ms | 1,281ms | **- 58%** |
| 인덱스 추가 후 조회 | 16ms | 16ms | **+ 0%** |
| 데이터 삭제 | 22,469ms | 46ms | **- 99%** |

<br>

➡️ 파티션을 적용하여 조회와 삭제 시 **불필요한 데이터 접근을 제거**함으로써, **대용량 테이블에서도 성능을 향상**시킬 수 있게 되었습니다. 특히 데이터 삭제 시 성능 개선이 크게 나타났습니다.

<br>

## 5. 데이터 이관

> 30일이 지난 알림 데이터는 더 이상 사용자에게 노출되지 않기 때문에, 기존에는 데이터(파티션)를 바로 삭제했습니다.
> 
> 하지만 새로운 요구사항으로 해당 데이터가 필요할 수도 있어, 단순 삭제보다는 아카이브 DB에 별도로 보관하는 방식이 더 적절합니다.
> 
> 이를 위해 [Spring Batch를 활용하여 대량의 데이터를 이관](/docs/issues/issue5-2.md)하도록 구현했습니다.

<br><br>

[뒤로가기](https://github.com/whxownss/celfeed/tree/docs-readme#4-%EC%95%8C%EB%A6%BC-%EC%84%9C%EB%B2%84%EC%9D%98-%EB%B0%9C%EC%A0%84)

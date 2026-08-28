# JdbcTemplate : 벌크 INSERT 최적화

<br>

## 1. 개요

> 셀럽(1)이 게시글을 작성하면 자신을 팔로우하고 있는 팬들(N)에게 알림이 전송됩니다.
> 
> 즉, 1건의 게시글을 저장하면서 N건의 알림 데이터도 함께 저장해야 합니다.

<br>

## 2. N건의 데이터를 저장하는 방법

> 모든 테스트에서 N은 10만으로, 팔로워가 10만 명인 셀럽이 게시글을 작성했을 때 알림 데이터 저장 방법별로 응답 시간이 어떻게 다른지 비교해 보겠습니다.

<br>

### ✏️ 방법 1. save()

<br>

**✅ SimpleJpaRepository.save()**
```java
List<Follow> followers = followRepository.findByToMember(postWriter);
followers.forEach(follower -> notificationRepository.save(Notification.create(...));
```
- 게시글을 저장한 후, 먼저 팔로워 목록을 조회합니다.
- 조회한 팔로워를 순회하면서 한 건씩 알림 데이터를 `save()` 합니다.

<br>

**✅ Postman**

![issue1-1](/docs/img/issue1-1.png)
- 게시글 작성 후 응답까지 약 3분 40초의 상당히 긴 시간이 소요되었습니다.
- 알림 테이블은 auto_increament로 PK 값을 생성하므로, 알림 엔티티의 `@GeneratedValue` 키 생성 전략으로 `IDENTITY`를 사용합니다.
- `hibernate.jdbc.batch_size` 옵션이 존재하지만, JPA는 엔티티를 영속성 컨텍스트에 저장하기 위해 키 값이 필요하며 `IDENTITY` 전략은 INSERT가 수행되어야 PK 값을 알 수 있기 때문에 배치 처리가 적용되지 않습니다.
- `save()` 1건당 DB 통신이 1회 발행하므로, 총 10만 건의 DB 통신으로 인해 병목이 발생하게 됩니다.

<br>

### ✏️ 방법 2. saveAll()

<br>

**✅ SimpleJpaRepository.saveAll()**

```java
List<Follow> followers = followRepository.findByToMember(postWriter);
List<Notification> notifications = followers.stream()
                                            .map(follower -> Notification.create(...))
                                            .toList();

notificationRepository.saveAll(notifications);
```

- 팔로워를 조회하고 저장할 알림 데이터 목록을 미리 만들어 놓습니다.
- 한 번의 `saveAll()` 호출로 만들어 놓은 알림 데이터를 전부 저장합니다.

<br>

**✅ Postman**

![issue1-2](/docs/img/issue1-2.png)
- 예상외로 더 오랜 시간이 걸렸습니다.
- 코드를 살펴보니 `saveAll()`도 결국 내부에서 반복문을 돌면서 `save()`를 하나하나 호출합니다.
    
    ```java
    @Repository
    @Transactional(readOnly = true)
    public class SimpleJpaRepository<T, ID> implements JpaRepositoryImplementation<T, ID> {
        ...
        @Transactional
        public <S extends T> List<S> saveAll(Iterable<S> entities) {
            List<S> result = new ArrayList<>();
        
            for (S entity : entities) {
                result.add(save(entity)); // save() 호출
            }
        
            return result;
        }
        
        @Transactional
        public <S extends T> S save(S entity) {
            if (entityInformation.isNew(entity)) {
                entityManager.persist(entity);
                return entity;
            } else {
                return entityManager.merge(entity);
            }
        }
    }
    ```

<br>

### ✏️ 방법 3. **batchUpdate**()

<br>

**✅ application.yml**

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/celfeed?rewriteBatchedStatements=true 
```
- JdbcTemplate의 `batchUpdate()`를 제대로 활용하려면 `rewriteBatchedStatements=true` 옵션을 설정해야 합니다.
- 만약 위 옵션을 설정하지 않으면, `ps.addBatch()`로 쿼리를 모으고 `ps.executeBatch()`로 모은 쿼리를 실행하더라도 INSERT가 단건으로 수행됩니다.

<br>

**✅ JdbcTemplate.batchUpdate**

```java
String sql = "insert into " +
	  " notification (id, receiver_id, actor_id, type, target_id, is_read, created_at) " +
	  " values (?, ?, ?, ?, ?, 'N', now())";

jdbcTemplate.batchUpdate(sql, batchList, batchList.size(), (ps, argument) -> {
    ps.setLong(1, argument.getReceiverId());
    ps.setLong(2, argument.getActorId());
    ps.setString(3, argument.getType());
    ps.setString(4, argument.getTargetType());
    ps.setLong(5, argument.getTargetId());
});
```
- JPA는 `IDENTITY` 전략 사용 시 배치 처리가 불가능하므로, 대량의 INSERT 쿼리 성능 개선을 위해 JdbcTemplate의 `batchUpdate()`를 사용했습니다.

<br>

**✅ Postman**

![issue1-3](/docs/img/issue1-3.png)
- `save()`를 통해 저장할 때보다 응답 시간이 **약 25배 단축**되었습니다.
- `batchSize`(세 번째 파라미터)만큼의 INSERT가 하나의 배치로 전송되어 DB 통신 횟수가 줄어들고, 그 결과 처리 속도가 개선되는 것을 확인할 수 있습니다.

<br>

## 3. 성능 비교

|  | 응답 시간 | save() 기준 비교 |
| :--- | ---: | ---: |
| **save()** | 219,420ms | + 0% |
| **saveAll()** | 228,960ms | + 4.35% |
| **batchUpdate()** | 8,700ms | **- 96.03%** |

➡️ **JdbcTemplate의 batchUpdate()** 적용 결과, JPA 방식 대비 응답 시간을 약 **96.03% 단축**할 수 있게 되었습니다.

<br><br>

[뒤로가기](https://github.com/whxownss/celfeed#4-%EC%95%8C%EB%A6%BC-%EC%84%9C%EB%B2%84%EC%9D%98-%EB%B0%9C%EC%A0%84)

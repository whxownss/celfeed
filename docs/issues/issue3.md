# Thread Pool 기반 알림 생성 비동기 처리 : 응답 시간 단축

## 1. 개요
> [알림 생성 서버와 알림 조회 서버를 분리](/docs/issues/issue2.md)했지만 다른 문제가 남아있습니다.
> 
> 게시글 작성, 게시글 좋아요, 팔로우 등의 작업에서 Sync하게 알림 생성을 요청하기 때문에 **알림 생성에 부하가 걸리면 더 중요한 비즈니스 로직에도 영향**을 끼치게 됩니다.
> 
> 예를 들어, 게시글 저장 로직에서 알림 저장까지 끝나야 비로소 사용자에게 응답이 나가게 됩니다.

<br>

## 2. Sync한 알림 생성 요청
> 게시글을 작성하면 팔로워 수만큼 알림 데이터가 생성되기 때문에 부하 테스트로 적당한 수단입니다.

<br>

✅ **Postman**

![issue3-01](/docs/img/issue3-01.png)
- 팔로워가 10만 명일 때 게시글 작성 후 응답까지 `8,700ms`가 소요되었습니다.
- 해당 결과는 대량의 알림 데이터 INSERT 쿼리를 최적화하여 기존(`219,420ms`) 대비 응답 시간을 약 96% 단축한 것이지만 , 절대적인 시간 기준으로는 여전히 짧다고 보기 어렵습니다.

<br>

## 3. Async Thread Pool
> 알림 생성을 요청하는 측에서 timeout 설정이나 Async하게 호출하는 방법도 있지만, 이는 알림 생성 서버를 신뢰하지 못해 설정하는 대비책에 불과합니다.
> 
> 차라리 **생성 서버 내부에서 비동기로 알림을 생성**하는 것이 더 나은 방법입니다.

<br>

✅ **AsyncConfig**
```java
@EnableAsync
@Configuration
public class AsyncConfig {

    @Bean
    public ThreadPoolTaskExecutor taskExecutor(ThreadPoolTaskExecutorBuilder builder) {
        return builder
                .corePoolSize(10)
                .maxPoolSize(10)
                .threadNamePrefix("async-")
                .build();
    }
}
```
- 우선 비동기 작업을 실행할 스레드 풀이 필요합니다.
- 스프링 부트에서는 `AsyncTaskExecutor`의 구현체인 `ThreadPoolTaskExecutor`가 자동으로 구성되지만, poolSize와 prefix를 명시적으로 설정하기 위해 별도로 빈을 등록했습니다.
- 또한 `@Async`가 정상적으로 동작하도록 `@EnableAsync`를 선언했습니다.
- 풀 사이즈를 설정할 때 주의할 점은 DB 커넥션 풀 사이즈 이상으로 설정할 경우, 모든 비동기 스레드가 커넥션을 독점할 수 있기 때문에 그보다 작게 설정해야 합니다. (현재 DB 커넥션 풀 크기 : 20)

<br>

✅ **PostService**
```java
@Transactional
public Long write(Long loginId, PostRequest postRequest) {
    ...
    Post savedPost = postRepository.save(post);           // 게시글 저장
    notificationService.sendWritePost(savedPost.getId()); // 알림 생성 요청
}
```
- 알림 생성을 호출하는 쪽의 코드 변화는 없습니다.

<br>

✅ **NotificationService**
```java
@Async
@Transactional(propagation = Propagation.REQUIRES_NEW)
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void sendNotification(WritePostEvent event) {
    // 알림 데이터 저장 및 전송 ...
}
```
- `@Async`를 적용하면 Spring의 AOP 프록시가 메서드 호출을 가로채고, 설정된 스레드 풀을 통해 호출 스레드와 분리된 별도의 스레드에서 작업을 수행합니다.
- 알림 생성 작업에서는 저장된 게시글의 id를 전달받아, 해당 id로 게시글을 조회한 뒤 알림 생성에 필요한 데이터를 구성합니다.
- 호출 스레드와 비동기 스레드는 서로 다른 트랜잭션 범위를 가지므로, 커밋 이전에 실행될 경우 게시글이 조회되지 않는 문제가 발생할 수 있습니다.
- 이 문제는 `@TransactionalEventListener`를 사용하여 트랜잭션 커밋 이후에 이벤트를 처리하도록 함으로써 해결할 수 있습니다. (물론 이때는 알림 생성을 요청할 때 이벤트 발행 코드 필요합니다.)

<br>

✅ **Postman**

![issue3-02](/docs/img/issue3-02.png)
- 팔로워가 10만 명일 때 게시글 작성 후 응답까지 `160.09ms`가 소요되었습니다.
- 동기 방식(`8,700ms`) 대비 응답 시간이 **약 98% 감소**했습니다.

<br>

➡️ 비동기 방식을 적용함으로써 **알림 요청과 알림 생성에 대한 경계**가 생겼습니다. 그 결과, 알림 생성에 부하가 발생하더라도 더 중요한 비즈니스 로직을 안정적으로 수행할 수 있게 되었습니다.

<br>

## 4. Graceful Shutdown 
> 배포 등의 이유로 애플리케이션 서버를 종료할 때, 진행 중이던 HTTP 요청이 있으면 해당 요청을 처리 후에 종료하는 것이 중요합니다.
> 
> 또한 모든 요청에 대한 응답은 나갔지만, 비동기로 처리 중인 작업이 있으면 해당 작업 역시 완료를 하고 애플리케이션을 종료해야 합니다.

<br>

**✅ application.yml**
```yaml
server:
    shutdown: graceful
```
- 해당 옵션 설정 시, 서버가 종료 시그널을 받으면 새로운 요청을 더 이상 받지 않고 기존의 요청이 전부 처리되면 서버를 종료합니다. (Spring Boot 3.4 버전부터 default)
- 요청 처리가 계속 지연될 경우를 대비해 `spring.lifecycle.timeout-per-shutdown-phase` 옵션으로 설정한 ms를 초과하면 서버는 종료됩니다. (default 30000)

<br>

✅ **AsyncConfig**
```java
@EnableAsync
@Configuration
public class AsyncConfig {

    @Bean
    public ThreadPoolTaskExecutor taskExecutor(ThreadPoolTaskExecutorBuilder builder) {
        return builder
                ...
                .awaitTermination(true) // 추가
                .awaitTerminationPeriod(Duration.ofSeconds(60)) // 추가
                .build();
    }
}
```
- 앞서 설정한 `server.shutdown` 옵션만으로도 `@Async`를 통한 비동기 작업까지 완료되면 서버를 종료하지만, 몇가지 문제가 있습니다.
    - `ThreadPoolTaskExecutor`의 작업 큐에 쌓인 작업들은 완료되지 않은 채로 서버가 종료됩니다.
    - 비동기 작업은 timeout을 다르게 설정하고 싶을 수도 있습니다.
- 작업 큐에 쌓인 작업도 모두 완료한 뒤에 서버를 종료하고, timeout도 따로 설정하기 위해 위와 같은 설정을 추가했습니다.

<br>

➡️ 위와 같은 설정들을 통해 애플리케이션을 종료하면, 새로운 요청은 더 이상 받지 않고 진행 중이던 HTTP 요청과 비동기 작업을 모두 끝내고 서버를 종료하는 **우아한 종료**를 적용할 수 있게 되었습니다.

<br>

## 5. 성능 테스트
> nGrinder를 활용한 부하 테스트로 **동기 방식**과 **비동기 방식**의 성능을 비교해 보았습니다.

<br>

✅ **공통 Test Script**
```groovy
@Test
public void test() {
    HTTPResponse loginResponse = request.POST("/api/members/login", ["id":"celeb10", "password":"1234123"])
    assertThat(loginResponse.statusCode, is(200))
    
    HTTPResponse writePostResponse = request.POST("/api/posts", ["content":"알림 테스트용 게시글 작성"])
    assertThat(writePostResponse.statusCode, is(201))
}
```
- 팔로워 수가 1만 명인 셀럽이 지속적으로 게시글을 작성하는 상황을 가정했습니다.
- 즉, 1건의 게시글 작성마다 1만 건의 row가 INSERT 됩니다.

<br>

**✅ 동기 방식**

![issue3-03](/docs/img/issue3-03.png)

![issue3-04](/docs/img/issue3-04.png) ![issue3-05](/docs/img/issue3-05.png)

![issue3-06](/docs/img/issue3-06.png)

<br>

**✅ 비동기 방식**

![issue3-07](/docs/img/issue3-07.png)

![issue3-08](/docs/img/issue3-08.png) ![issue3-09](/docs/img/issue3-09.png)

![issue3-10](/docs/img/issue3-10.png)

<br>

**✅ 성능 비교**
|  | Sync | Async | 비교 |
| :--- | ---: | ---: | ---: |
| **TPS** | 2.0 | 73.8 | **+ 3,590%** |
| **Peak TPS** | 5.5 | 173.0 | **+ 3,045%** |
| **Mean Test Time** | 5,108.07ms | 133.87ms | **- 97%** |
| **Max CPU Usage** | 83.0% | 55.9% | **- 33%** |
| **Executed Tests** | 339 | 13,064 |  |
| **Errors** | 0 | 0 |  |
- vuser는 10(2 * 5)으로 설정하고 3분간 테스트를 진행했습니다.
- 동기 방식에 비해 비동기 방식에서 눈에 띄는 성능 향상을 보였습니다.
    - TPS(초당 처리한 트랜잭션 수)가 **약 37배 증가**했습니다.
    - MTT(평균 테스트 완료 시간)는 **약 38배 감소**했습니다.
    - JVM 프로세스의 최대 CPU 사용량은 **약 1.5배 감소**했습니다.

<br>

➡️ **비동기 방식**을 적용하여 알림 생성과 같은 **무거운 작업을 별도의 스레드에서 병렬 실행**함으로써, 요청 처리량과 응답 시간 모두 크게 개선되었습니다.

<br><br>

[뒤로가기](https://github.com/whxownss/celfeed/tree/docs-readme#4-%EC%95%8C%EB%A6%BC-%EC%84%9C%EB%B2%84%EC%9D%98-%EB%B0%9C%EC%A0%84)

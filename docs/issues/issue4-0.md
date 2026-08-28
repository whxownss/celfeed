# Message Queue 도입 : 알림 데이터 보호

<br>

## 1. 개요

> [알림 생성과 알림 조회를 분리](/docs/issues/issue2.md)했고, [알림 생성을 비동기로 처리](/docs/issues/issue3.md)했지만 문제는 여전히 남아있습니다.
> 
> 비동기로 알림 생성 중에 생성 서버가 어떠한 이유로 다운되면 알림 데이터가 유실될 수 있습니다.

<br>

## 2. 메시지 큐 선택

> 메시지 큐 도입의 목적은 **알림 데이터 유실을 방지**하고, 트래픽이 몰리는 상황에서도 **안정적인 비동기 이벤트 처리**가 가능하도록 하기 위함입니다.
> 
> 여러 후보 중에서 디스크 기반 저장 구조를 통해 **메시지 영속성**을 보장하고 **대량의 이벤트를 높은 처리량으로 처리**할 수 있는 `Apache Kafka`를 선택했습니다.

<br>

## 3. Spring Kafka

> 게시글 작성, 게시글 좋아요, 팔로우 신청 등의 이벤트가 발생하면 알림이 생성됩니다. 이 중 **게시글 작성**은 한 건의 이벤트가 팔로워 수만큼의 알림을 만들어내는 **Fan-out 구조**에 해당합니다.
> 
> 게시글 작성 이벤트가 발생하고 알림이 생성되는 과정에 대해 알아보겠습니다.

<br>

✅ **PostService**
```java
@Service
@RequiredArgsConstructor
public class PostService {
    private final ApplicationEventPublisher publisher;
    ...
    @Transactional
    public Long write(Long loginId, PostRequest postRequest) {
        ...
        Post savedPost = postRepository.save(post);
        
        publisher.publishEvent(new WritePostEvent(KafkaTopicConst.WRITE_POST, savedPost.getId()));
        
        return savedPost.getId();
    }
}
```
- 게시글을 DB에 저장한 후, 게시글 작성 이벤트를 발행했습니다.
- `WRITE_POST` 토픽 정보와 게시글 id를 함께 전달했습니다.

<br>

**✅ KafkaEventListener**
```java
@Component
@RequiredArgsConstructor
public class KafkaEventListener {
    private final KafkaTemplate<String, Long> kafkaTemplate;
    ...
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void writePost(WritePostEvent event) {
        kafkaTemplate.send(event.getTopic(), event.getPostId());
    }
}
```
- `WRITE_POST` 토픽을 구독하는 Consumer는 게시글 id로 게시글을 조회하고, 필요한 알림 데이터를 생성하기 때문에 게시글 저장이 commit된 후 메시지가 전송되도록 했습니다.
- `WRITE_POST` 토픽에 게시글 id를 `send()` 합니다.

<br>

✅ **NotificationFanOutListener**
```java
@Component
@RequiredArgsConstructor
public class NotificationFanOutListener {
    private final int BATCH_SIZE = 100_000;
    private final KafkaTemplate<String, WritePostMessage> kafkaTemplate;
    ...
    @KafkaListener(
            topics = KafkaTopicConst.WRITE_POST,
            groupId = KafkaGroupConst.NOTI_FANOUT
    )
    public void fanOutListener(Long postId) {
        ...
        while (true) {
            List<Long> followerIds = followRepository.BATCH_SIZE씩조회();
            if (followerIds.isEmpty()) break;
        
            kafkaTemplate.send(
                    KafkaTopicConst.NOTI_BATCH,
                    new WritePostMessage(followerIds, postWriter.getId(), postId)
            );
        }
    }
}
```
- `WRITE_POST` 토픽을 구독하는 Consumer는 알림 생성 작업을 한 번에 처리하지 않고, `BATCH_SIZE`씩 팔로워를 나누어 조회한 뒤, `NOTI_BATCH` 토픽에 `send()` 하여 알림 생성을 처리합니다.
- 알림 생성을 다른 Consumer에서 나누어 처리하는 이유는, 하나의 Consumer에서 무거운 INSERT 작업을 처리할 경우 병목이 발생할 수 있기 때문입니다.
- 또한 트래픽이 집중된 상황에서 팔로워 수가 매우 많은 회원의 경우, 모든 팔로워를 한 번에 조회하면 OOM가 발생할 수도 있습니다.
- `BATCH_SIZE씩조회()` 쿼리는 [커서 기반 페이징](/docs/issues/issue4-1.md) 방식을 선택했습니다.

<br>

✅ **NotificationListener**
```java
@Component
@RequiredArgsConstructor
public class NotificationListener {
    ...
    @KafkaListener(
            topics = KafkaTopicConst.NOTI_BATCH, concurrency = "3",
            containerFactory = "writePostNotiContainerFactory"
    )
    public void writePostNotificationListener(WritePostMessage message) {
        // 알림 데이터 DB 저장
        // SSE로 알림 전송
    }
}
```
- `NOTI_BATCH` 토픽을 구독하는 Consumer는 `NotificationFanOutListener`에서 전달한 `BATCH_SIZE`만큼의 팔로워에 대해 알림 데이터를 저장하고, 비동기 방식으로 SSE를 통해 클라이언트에 알림을 전송합니다.
- `NOTI_BATCH` 토픽은 3개의 파티션으로 구성되어 있으므로, 동시 처리(`concurrency`) 인스턴스 수를 파티션 수에 맞게 조정하여 처리 병목을 완화합니다.

<br>

## 4. 신뢰성 있는 Kafka

> Kafka에서 **정확히 한 번**(Exactly Once)이란, Producer가 브로커에게 메시지를 보낼 때 **유실되지 않고**(at least once) **중복 없이**(at most once) 정확히 한 번만 전송하도록 보장하는 개념입니다.
> 
> 해당 개념을 통해 신뢰성 있는 Kafka 애플리케이션을 개발할 수 있습니다.

<br>

### ✏️ 멱등성 프로듀서
> **멱등성**은 동일한 작업을 여러 번 수행하더라도 동일한 결과가 나타나는 성질을 의미합니다.
> 
> **멱등성 Producer**는 Retry로 인해 같은 데이터를 여러 번 전송하더라도 해당 데이터가 Kafka 클러스터에 한 번만 저장되도록 보장합니다.
> 
> 데이터의 중복 저장이 없도록 Producer에 멱등성을 적용하는 과정에 대해 알아보겠습니다.

<br>

**✅ Producer AcksMode**

<aside>

- Producer는 메시지를 브로커로 전송한 뒤, 브로커로부터 Ack 응답을 받습니다.
- 만약 브로커로부터 Ack가 오지 않는다면 메시지를 재전송(Retry)하게 됩니다.
- `AcksMode`는 브로커가 어떤 조건을 만족해야 Ack를 보내는지를 정의합니다.
- 즉, 메시지를 언제 전송 성공으로 간주할 것인지를 결정하는 설정입니다.
</aside>

<br>

**✅ acks**

<aside>

- `acks=all` 설정 시, 리더 브로커뿐만 아니라 ISR(In-Sync Replicas) 전체가 메시지 저장을 완료해야 Ack 응답을 받습니다.
- 덕분에 리더 브로커가 갑자기 다운되어 레플리카에서 데이터를 복제하지 못하는 상황에도 데이터가 손실될 수 있는 위험이 크게 줄어들었습니다.
- 하지만 아래와 같은 상황에서 **데이터가 중복으로 저장**될 수 있는 문제가 있습니다. ([이미지 출처](https://medium.com/@shesh.soft/kafka-idempotent-producer-and-consumer-25c52402ceb9))
    
    ![issue4-0-01](/docs/img/issue4-0-01.png)
    1. "y"라는 메시지를 Producer가 브로커에게 전송합니다.
    2. 해당 메시지는 브로커에 의해 정삭적으로 파티션에 저장됩니다.
    3. **네트워크 지연 등의 이유로 Producer에게 Ack가 실패**합니다.
    4. Producer는 오류로 판단하고 **동일한 메시지를 다시 전송**합니다.
    5. 브로커에는 "y"가 **중복으로 저장**됩니다.
</aside>

<br>

**✅ enable.idempotence**

<aside>

- `enable.idempotence=true` 설정 시, 멱등성 Producer를 사용할 수 있습니다.
- 아래와 같은 과정을 통해 멱등성을 보장할 수 있습니다. ([이미지 출처](https://medium.com/@shesh.soft/kafka-idempotent-producer-and-consumer-25c52402ceb9))
    
	![issue4-0-02](/docs/img/issue4-0-02.png)
	1. 멱등성 Producer는 브로커에게 메시지를 보낼 때마다 PID(Producer unique ID)를 포함하며, 각 메시지는 순차적으로 증가하는 Seq 번호를 받습니다.
	2. Producer가 메시지를 보내는 각 토픽 파티션마다 별도의 Seq가 유지됩니다.
	3. **네트워크 지연 등의 이유로 Producer에게 Ack가 실패**합니다.
	4. Producer는 오류로 판단하고 **동일한 메시지를 다시 전송**합니다.
	5. 브로커는 Producer의 요청이 토픽 파티션 내의 같은 PID에 대해 마지막으로 커밋된 메시지보다 **Seq가 정확히 1만큼 크지 않을 경우, Producer의 요청을 거부**합니다.
</aside>

<br>

**✅ max.in.flight.requests.per.connection**

<aside>

- `max.in.flight.requests.per.connection` 옵션은 브로커와의 연결에서 Producer가 Ack를 기다리는 동안 보낼 수 있는 요청의 최대 개수를 의미합니다.
- 멱등성을 보장하는 Producer에서는 해당 옵션을 통해 전송되는 여러 메시지 간의 **순서 보장과 중복 저장 방지**를 도와줍니다.
- 주의할 점으로는, 해당 옵션의 값이 5를 초과한 상태에서 멱등성 Producer를 사용하려고 하면 예외가 발생합니다.
</aside>

<br>

**✅ KafkaProducerConfig**

```java
@Configuration
public class KafkaProducerConfig {
		
    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;
    
    @Bean
    public ProducerFactory<String, Long> producerFactory() {
        Map<String, Object> configs = new HashMap<>();
        configs.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configs.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configs.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, LongSerializer.class);

        // 멱등성 보장을 위한 설정 (생략 가능)
        configs.put(ProducerConfig.ACKS_CONFIG, "all");
        configs.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        configs.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);
        configs.put(ProducerConfig.RETRIES_CONFIG, Integer.MAX_VALUE);

        return new DefaultKafkaProducerFactory<>(configs);
    }
    ...
}
```
- 코드상의 멱등성 보장을 위한 설정들은 전부 기본값으로 설정되어 있기 때문에 생략 가능합니다.

<br>

➡️ **멱등성 Producer** 덕분에, 메시지 재전송이 발생하더라도 **중복 저장되지 않고 순서가 보장**되는 데이터 전송을 구현할 수 있게 되었습니다.

<br>

### ✏️ Kafka 트랜잭션
> **Exactly Once**를 보장하기 위해서는 **멱등성 Producer**뿐만 아니라 **트랜잭션** 개념도 필요합니다.
> 
> 멱등성 Producer는 Retry 시 중복 저장을 방지해 줄 뿐, 특정 상황에서는 여전히 메시지가 중복 전송되어 처리될 수 있습니다.
> 
> 메시지가 중복 전송되는 상황을 살펴보고, **Kafka 트랜잭션**을 통해 이를 어떻게 해결할 수 있는지 알아보겠습니다.

<br>

**✅ 게시글 작성 후 알림 생성 처리 흐름**

<aside>

1. 게시글을 DB에 저장한 뒤, `WRITE_POST` 토픽에 메시지 `send()`
2. `WRITE_POST` Consumer에서 글 작성자의 팔로워를 `BATCH_SIZE` 단위로 조회하고, 3개의 파티션으로 구성된 `NOTI_BATCH` 토픽에 메시지 `send()`
3. `NOTI_BATCH` Consumer에서는 전달받은 팔로워를 대상으로 알림 데이터 저장 및 전송
</aside>

<br>

**✅ 메시지 중복 전송**

<aside>

- 위의 2번 흐름에서, `NOTI_BATCH` 토픽으로의 메시지 전송은 성공했지만, 이후 장애가 발생하여 `WRITE_POST` 메시지에 대한 offset commit을 실패했다고 가정하겠습니다.
- 장애로 인해 애플리케이션이 재시작되면, offset commit 실패로 동일 메시지를 다시 처리하게 되고, 그 결과 `NOTI_BATCH` 토픽으로 **메시지가 중복 전송**될 수 있습니다.
- 이러한 중복을 방지하기 위해서는 **offset commit과 메시지 전송을 하나의 트랜잭션**으로 묶어 처리해야 합니다.
</aside>

<br>

**✅ KafkaProducerConfig**

```java
@Configuration
public class KafkaProducerConfig {

    @Bean // [1]
    public ProducerFactory<String, WritePostMessage> wirtePostNotiProducerFactory() {
        Map<String, Object> configs = new HashMap<>();
        // bootstrap servers, key/value serializer 설정 ...

        DefaultKafkaProducerFactory<String, WritePostMessage> factory = new DefaultKafkaProducerFactory<>(configs);
        factory.setTransactionIdPrefix("tx-"); // [2]

        return factory;
    }

    @Bean
    public KafkaTransactionManager<String, WritePostMessage> kafkaTransactionManager() {
        return new KafkaTransactionManager<>(wirtePostNotiProducerFactory()); // [3]
    }

    @Bean
    public KafkaTemplate<String, WritePostMessage> writePostNotiKafkaTemplate() {
        return new KafkaTemplate<>(wirtePostNotiProducerFactory());
    }
    ...
}
```
**[1]** `NOTI_BATCH` 메시지 전송에 사용되는 Producer를 생성하는 `ProducerFactory`입니다.

**[2]** `DefaultKafkaProducerFactory`에 `transactionIdPrefix`를 설정하면 해당 팩토리는 Producer를 생성할 때 **트랜잭션 Producer**를 생성합니다.

**[3]** `KafkaTransactionManager`는 `PlatformTransactionManager`의 구현체 클래스로, Producer의 트랜잭션을 관리합니다.

<br>

**✅ KafkaConsumerConfig**
```java
@Configuration
public class KafkaConsumerConfig {

    @Bean // [1]
    public ConsumerFactory<String, Long> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        // bootstrap servers, key/value deserializer 설정 ...
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false); // [2]

        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Long> kafkaListenerContainerFactory( 
            KafkaTransactionManager<String, WritePostMessage> kafkaTransactionManager // [3]
	) {
		
        ConcurrentKafkaListenerContainerFactory<String, Long> factory = new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(consumerFactory());
        factory.getContainerProperties()
 				.setKafkaAwareTransactionManager(kafkaTransactionManager); // [4]

        return factory;
    }
    
    @Bean // [5]
    public ConsumerFactory<String, WritePostMessage> writePostNotiConsumerFactory() {
        Map<String, Object> props = new HashMap<>();
        // bootstrap servers, group id, auto commit 설정 ...
        props.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_commited"); // [6]

        return new DefaultKafkaConsumerFactory<>(
				props,
				new StringDeserializer(),
				new JacksonJsonDeserializer<>(WritePostMessage.class)
        );
    }
    ...
}
```
**[1]** `WRITE_POST` 메시지를 소비하는 Consumer를 생산하는 `ConsumerFactory`입니다.

**[2]** 트랜잭션과 함께 offset commit이 이뤄지도록 자동 커밋을 비활성화했습니다.

**[3]** `KafkaProducerConfig`에서 등록한 `KafkaTransactionManager`를 주입받습니다.

**[4]** 리스너 컨테이너에 `KafkaAwareTransactionManager`를 설정하면 컨테이너는 리스너를 호출하기 전에 트랜잭션을 시작합니다. 또한 리스너에서 수행하는 모든 `KafkaTemplate` 작업은 트랜잭션에 포함되고 리스너가 레코드를 성공적으로 처리하면 컨테이너는 `producer.sendOffsetsToTransaction()`을 사용하여 트랜잭션 매니저에게 offset을 전송합니다.

**[5]** `NOTI_BATCH` 메시지를 소비하는 Consumer를 생산하는 `ConsumerFactory`입니다.

**[6]** `NOTI_BATCH` Consumer에서 commit된 메시지만 읽을 수 있도록 설정했습니다.

<br>

**✅ 트랜잭션 적용 후**

<aside>

- 앞서, `NOTI_BATCH` 토픽으로 메시지 전송 후 장애가 발생하여 `WRITE_POST`에 대한 offset commit을 실패했을 때 메시지가 중복 전송될 수 있는 것을 확인했습니다.
- 트랜잭션 적용 후 장애가 발생하면, `KafkaTemplate`으로 전송한 메시지들이 롤백되고 offset commit 또한 발생하지 않습니다.
- 문제 없이 트랜잭션이 커밋되면 **offset commit과 전송한 메시지도 커밋**하게 됩니다.
</aside>

<br>

➡️ Kafka 트랜잭션을 통해 **offset commit과 메시지 전송을 하나의 트랜잭션**으로 묶어 처리했습니다. 이로써, **정확히 한 번**을 보장할 수 있게 되었고, 신뢰성 있는 Kafka 애플리케이션이 되었습니다.

<br>

## 5. 성능 테스트
> nGrinder를 활용한 부하 테스트로 **메시지 큐 도입 전과 후의 성능을 비교**했습니다.

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

**✅ 메시지 큐 도입 전**

![issue4-0-03](/docs/img/issue4-0-03.png)

![issue4-0-04](/docs/img/issue4-0-04.png) ![issue4-0-05](/docs/img/issue4-0-05.png)

![issue4-0-06](/docs/img/issue4-0-06.png)

<br>

**✅ 메시지 큐 도입 후**

![issue4-0-07](/docs/img/issue4-0-07.png)

![issue4-0-08](/docs/img/issue4-0-08.png) ![issue4-0-09](/docs/img/issue4-0-09.png)

![issue4-0-10](/docs/img/issue4-0-10.png)

<br>

**✅ 성능 비교**
|  | 도입 전 | 도입 후 | 비교 |
| :--- | ---: | ---: | ---: |
| **TPS** | 94.6 | 255.1 | **+ 170%** |
| **Peak TPS** | 210.5 | 327.5 | **+ 56%** |
| **Mean Test Time** | 1,058.04ms | 390.38ms | **- 63%** |
| **Max CPU Usage** | 56.2% | 36.1% | **- 36%** |
| **Executed Tests** | 16,186 | 44,098 |  |
| **Errors** | 0 | 0 |  |
- vuser는 100(5 * 20)으로 설정하고 3분간 테스트를 진행했습니다.
- 메시지 큐를 도입했을 때 유의미한 변화가 확인되었습니다.
    - TPS(초당 처리한 트랜잭션 수)가 **약 2.7배 증가**했습니다.
    - MTT(평균 테스트 완료 시간)는 **약 2.7배 감소**했습니다.
    - JVM 프로세스의 최대 CPU 사용량은 **약 1.5배 감소**했습니다.
    - TPS 그래프가 좀 더 안정적인 모습을 띠고있습니다.

<br>

**✅ 두 방식의 차이**

<aside>

- 두 방식 모두 비동기로 처리되지만, 이들 간의 차이는 **작업이 쌓이는 위치**에 있습니다.
- 메시지 큐 도입 전 `@Async` 사용 방식에서는
    - 요청한 알림 생성 작업이 `AsyncTaskExecutor`의 내부 큐에 쌓입니다.
    - 부하가 JVM 내부에 쌓이므로 요청 스레드에 직접적인 영향이 발생합니다.
    - 스레드 풀 크기를 늘리더라도, 부하는 여전히 JVM 내부에 쌓이며 컨텍스트 스위칭 증가와 DB 커넥션 풀 고갈 같은 부작용이 발생하므로 근본적인 해결책은 아닙니다.
- 메시지 큐를 도입했을 때는
    - 작업이 Kafka 브로커의 Topic에 쌓입니다.
    - 부하가 JVM 외부에 쌓이므로, 더 많은 트래픽을 안정적으로 처리할 수 있습니다.
</aside>

<br>

![issue4-0-11](/docs/img/issue4-0-11.png)
➡️ **Kafka를 도입**하여, **메시지 유실 없이 안정적으로 대량의 알림 생성 작업을 처리**할 수 있게 되었습니다.

<br><br>

[뒤로가기](https://github.com/whxownss/celfeed#4-%EC%95%8C%EB%A6%BC-%EC%84%9C%EB%B2%84%EC%9D%98-%EB%B0%9C%EC%A0%84)

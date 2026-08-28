# [JPA] Snowflake ID 키 생성기 적용

<br>

## 1. 개요

> 기존 알림 테이블은 단일 DB 인스턴스에서 auto_increment 기반의 id 컬럼을 기본 키로 사용합니다.
> 
> 그러나 샤딩을 적용하면서, 각 DB 인스턴스가 독립적으로 auto_increment 값을 생성하게 되므로 id 컬럼의 유일성을 보장할 수 없게 되었습니다.
> 
> 따라서 DB에 의존하는 auto_increment 방식이 아닌 **애플리케이션에서 유니크한 ID를 생성**하는 방식으로 전환했습니다.

<br>

## 2. **UUID와 비교**
 
> UUID를 사용해도 충분히 유일 키를 생성할 수 있지만 128비트의 저장 공간이 필요합니다. 이는 64비트의 공간이 필요한 Snowflake 방식의 2배입니다.
> 
> 또한 클러스터링 인덱스 구조를 사용하는 InnoDB에서, 무작위로 생성되는 UUID(v4)는 인덱스 삽입 위치 역시 무작위이지만 시간 순으로 계속 증가하는 Snowflake ID는 항상 B-Tree의 맨 끝에 삽입됩니다.
> 
> 비교를 통해 **저장 공간과 인덱스 성능 측면에서 유리한 Snowflake ID**를 사용하기로 결정했습니다.

<br>

## 3. 적용

> [Twitter가 Scala로 작성한 Snowflake 코드](https://github.com/twitter-archive/snowflake/blob/snowflake-2010/src/main/scala/com/twitter/service/snowflake/SnowflakeServer.scala)를, [Java로 변환한 코드](https://github.com/callicoder/java-snowflake/blob/master/src/main/java/com/callicoder/snowflake/Snowflake.java)를 참고했습니다.

<br>

**✅ SnowflakeIdGenerator**
```java
@Component
public class SnowflakeIdGenerator implements IdentifierGenerator {
    private static final int UNUSED_BITS = 1;
    private static final int EPOCH_BITS = 41;
    private static final int NODE_ID_BITS = 10;
    private static final int SEQUENCE_BITS = 12;

    private static final long maxNodeId = (1L << NODE_ID_BITS) - 1;
    private static final long maxSequence = (1L << SEQUENCE_BITS) - 1;
    private static final long DEFAULT_CUSTOM_EPOCH = 1420070400000L;

    private volatile long lastTimestamp = -1L;
    private volatile long sequence = 0L;
    private final long nodeId = 0; // id를 생성하는 서버가 늘어날 때 구분용

    @Override
    public Object generate(SharedSessionContractImplementor sharedSessionContractImplementor, Object o) {
        return nextId();
    }

    public synchronized long nextId() {
        long currentTimestamp = timestamp();

        if(currentTimestamp < lastTimestamp) {
            throw new IllegalStateException("Invalid System Clock!");
        }

        if (currentTimestamp == lastTimestamp) {
            sequence = (sequence + 1) & maxSequence;
            if(sequence == 0) {
                currentTimestamp = waitNextMillis(currentTimestamp);
            }
        } else {
            sequence = 0;
        }

        lastTimestamp = currentTimestamp;

        return currentTimestamp << (NODE_ID_BITS + SEQUENCE_BITS)
                | (nodeId << SEQUENCE_BITS)
                | sequence;
    }

    private long timestamp() {
        return Instant.now().toEpochMilli() - DEFAULT_CUSTOM_EPOCH;
    }

    private long waitNextMillis(long currentTimestamp) {
        while (currentTimestamp == lastTimestamp) {
            currentTimestamp = timestamp();
        }
        return currentTimestamp;
    }
}
```
- JPA가 기본적으로 제공하는 ID 생성기 외에 임의로 개발한 생성기 사용을 위해 `IdentifierGenerator` 인터페이스를 구현했습니다.
- `generate()` 메서드를 오버라이딩해서 키 생성 로직을 구현했습니다.

<br>

**✅ SnowflakeId**
```java
@IdGeneratorType(SnowflakeIdGenerator.class)
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface SnowflakeId {
}
```
- Hibernate 6.5부터 필드에 직접 생성기를 지정했던 `@GenericGenerator`가 deprecated 되었습니다.
- `@IdGeneratorType`을 통해 키 생성기를 지정할 수 있습니다.

<br>

**✅ Notification**
```java
@Entity
@Getter
@NoArgsConstructor(access = PROTECTED)
public class Notification extends BaseCreateEntity {

    @Id
    @SnowflakeId
    private Long id;
    ...
}
```

<br>

➡️ Snowflake ID 역시 64비트의 Long 타입을 사용하므로 기존 id 컬럼의 BIGINT 타입을 그대 유지하면서 **분산 DB 환경에서도 전역적으로 고유한 ID**를 생성할 수 있게 되었습니다.

<br><br>

[뒤로가기](https://github.com/whxownss/celfeed#4-%EC%95%8C%EB%A6%BC-%EC%84%9C%EB%B2%84%EC%9D%98-%EB%B0%9C%EC%A0%84)

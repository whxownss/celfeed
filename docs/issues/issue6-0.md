# 알림 DB 샤딩 : 트래픽 분산

<br>

## 1. 개요

> [파티션을 적용](/docs/issues/issue5-0.md)하여 대량의 데이터가 있는 알림 테이블에서 데이터 조회 및 삭제 시 쿼리 속도가 느려지는 문제를 해결했습니다.
> 
> 하지만 파티셔닝은 단일 DB 인스턴스 내에서 데이터 접근 범위를 줄이는 데에는 효과적이지만, 트래픽 집중 시 DB 자체의 부하를 분산시키는 데는 한계가 있습니다.
> 
> 따라서 **트래픽을 여러 DB 인스턴스로 분산**시킬 수 있는 샤딩을 적용하게 되었습니다.

<br>

## 2. Snowflake ID

> 샤딩과 같은 분산 DB 환경에서 전역적으로 유니크한 ID를 생성하기 위해 [Snowflake ID를 도입](/docs/issues/issue6-1.md)했습니다.

<br>

## 3. 레인지 샤딩 vs 모듈러 샤딩

> 두 샤딩 방식 중 어느 방식을 도입할지 결정하기 전에, 알림 테이블의 구조를 먼저 확인해 보겠습니다.

<br>

**✅ Notification 테이블**
```sql
create table notification (
    id          bigint      not null,
    receiver_id bigint      not null,
    actor_id    bigint      not null,
    type        varchar(20) not null,
    target_id   bigint      not null,
    is_read     varchar(1)  not null,
    created_at  datetime(6) not null,
    
    primary key (id, created_at),
    key idx_receiver_create (receiver_id, created_at desc)
)
partition by range (to_days(created_at)) (
    partition p202511 values less than (to_days('2025-12-01')),
    partition p202512 values less than (to_days('2026-01-01')),
    partition p202601 values less than (to_days('2026-02-01')),
    partition pmax    values less than maxvalue
);
```
- `created_at` 컬럼을 파티션 키로 사용하므로, id 컬럼과 함께 복합 키로 PK를 구성하고 있습니다.
- 대부분의 쿼리가 **”A의 최근 30일 알림”** 형식으로 조회되므로, `receiver_id` 컬럼과 `created_at` 컬럼으로 인덱스를 추가했습니다.

<br>

**✅ Range sharding**

![issue6-0-1](/docs/img/issue6-0-1.png)

- 샤딩 키의 범위를 기준으로 DB를 특정하는 방식입니다.
- DB를 증설할 때 재정렬 비용이 들지 않는다는 장점이 있습니다.
- 어떤 컬럼이 샤딩 키로 적당할까?
    - `created_at` 컬럼은 최신 알림이 항상 마지막 샤드로 라우팅되므로, 트래픽을 분산하고자 하는 목적에 부합하지 않습니다.
    - `receiver_id`는 회원 ID로, 늘어나는 회원에 맞게 범위를 지정하기 다소 애매한 부분이 있습니다.

<br>

**✅ Modular sharding**

![issue6-0-2](/docs/img/issue6-0-2.png)

- 샤딩 키를 모듈러 연산한 결과로 DB를 특정하는 방식입니다.
- 레인지 샤딩에 비해 데이터가 균일하게 분산되는 장점이 있습니다.
- 알림 테이블은 주로 사용자 단위로 접근하므로, `receiver_id` 기준의 모듈러 샤딩이 트래픽 분산에 적합합니다.
- **DB를 증설하는 과정에서 이미 적재된 데이터의 재정렬**이 필요하다는 단점이 있습니다.

<br>

➡️ 두 방식 모두 장단점이 있지만 레인지 샤딩의 경우 현재 서비스에 적합한 샤딩 키를 고르기 애매한 문제가 있기 때문에 **모듈러 샤딩 방식의 단점을 인지하고 도입**하기로 결정했습니다.

<br>

## 4. Sharding 적용

> 알림 테이블에 샤딩을 적용하기 위해 기존 DB와 분리된 **신규 DB 2개를 생성**했습니다.
> 
> 분리된 DB로 인해 회원 엔티티와 연관관계를 맺고 있던 알림 엔티티에 문제가 발생하여, 연관관계를 제거하고 [회원의 id를 참조하는 방식으로 변경](/docs/issues/issue6-2.md)했습니다.

> 본격적으로 샤딩을 적용하면서 애플리케이션에 어떠한 변화가 있었는지 알아보겠습니다. ([참고한 링크](https://techblog.woowahan.com/2687))

<br>

**✅ application.yaml**
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/celfeed
    username: root
    password: root
    driver-class-name: com.mysql.cj.jdbc.Driver
    maximum-pool-size: 10
    minimum-idle: 10

celfeed:
  datasource:
    notification:
      shards:
        - shard_no: 0
          name: notification-shard-0
          url: jdbc:mysql://localhost:3307/notification
          username: root
          password: root
        - shard_no: 1
          name: notification-shard-1
          url: jdbc:mysql://localhost:3308/notification
          username: root
          password: root
```
- 기존 DB(`spring.datasource`)와 알림 DB(`celfeed.datasource`) 연결을 위한 정보들입니다.
- 기존 DB 연결을 위한 `DataSource`는 Spring Boot가 자동으로 만들어주는 것을 사용할 것입니다.
- 알림 DB 연결을 위한 `DataSource`는 직접 등록하여 사용할 것입니다.

<br>

**✅ ShardingDataSourceProperty**
```java
@Getter
@Setter
public class ShardingDataSourceProperty {

    private List<Shard> shards;
    
    @Getter
    @Setter
    public static class Shard {
        private int shardNo;
        private String name;
        private String url;
        private String username;
        private String password;
    }
}
```
- yaml 파일에서 설정한 알림 DB 연결 정보를 매핑하기 위한 클래스를 새로 정의합니다.

<br>

**✅ NotificationDataSourceConfig**
```java
@Setter
@Configuration
@ConfigurationProperties(prefix = "celfeed.datasource") // [1]
public class NotificationDataSourceConfig {

    private ShardingDataSourceProperty notification; // [1]

    @Qualifier("notification")
    @Bean(defaultCandidate = false)
    public DataSource notificationDataSource() {
        DataSourceRouter router = new DataSourceRouter(); // [2]
        Map<Object, Object> dataSourceMap = new LinkedHashMap<>(); // [3]

        for (int i = 0; i < notification.getShards().size(); i++) {
            ShardingDataSourceProperty.Shard shard = notification.getShards().get(i);

            DataSource dataSource = dataSource(shard.getUsername(), shard.getPassword(), shard.getUrl());
		            
            dataSourceMap.put(shard.getShardNo() + " " + shard.getName(), dataSource);
        }

        router.setTargetDataSources(dataSourceMap); // [3]
        router.afterPropertiesSet();

        return new LazyConnectionDataSourceProxy(router); // [4]
    }

    private DataSource dataSource(String username, String password, String url) {
        HikariDataSource dataSource = new HikariDataSource();
        ...
        return dataSource;
    }
}
```
**[1]** yaml 파일에 설정한 `celfeed.datasource.notification` 정보를 매핑해줍니다.

**[2]** `AbstractRoutingDataSource`를 구현한 `DataSourceRouter`는 샤딩 처리한 2개의 알림 DB에 대해 라우팅하는 역할을 합니다.

**[3]** 여러 `DataSource` 정보를 담아 `DataSourceRouter`에 등록합니다.

**[4]** 샤딩에 따른 멀티 데이터소스 구성을 위해 필요한 클래스로, 라우터가 `determineCurrentLookupKey()` 메서드를 통해 데이터소스를 결정할 수 있게 합니다.

<br>

**✅ application.yaml**
```yaml
spring:
  datasource:
    ...

celfeed:
  datasource:
    ... 
  sharding:
    notification:
      strategy: MODULAR
      mod: 2
```
- 샤딩 전략에 대한 상세 정보(`celfeed.sharding.notification`)를 추가합니다.
- 모듈러 전략에서 mod는 DB 인스턴스의 수와 동일합니다.

<br>

**✅ ShardingTarget**
```java
public enum ShardingTarget {
    NOTIFICATION
}
```
- 알림 테이블뿐만 아니라 다른 테이블에도 샤딩을 적용하게 될 경우 값을 추가해 주면 됩니다.

<br>

**✅ ShardingStrategy**
```java
public enum ShardingStrategy {
    MODULAR
}
```
- 모듈러 샤딩뿐만 아니라 레인지 샤딩 등 다른 샤딩 전략이 도입될 경우 값을 추가해 주면 됩니다.

<br>

**✅ ShardingProperty**
```java
@Getter
@Setter
public class ShardingProperty {
    private ShardingStrategy strategy;
    private int mod;
}
```
- yaml 파일에서 추가한 샤딩 정보(`celfeed.sharding.notification`)를 매핑하기 위한 클래스입니다.
- 새로운 샤딩 전략이 추가될 경우 해당 전략에 필요한 속성을 추가해 주면  됩니다.

<br>

**✅ ShardingConfig**
```java
@Setter
public class ShardingConfig {
    private static Map<ShardingTarget, ShardingProperty> shardingPropertyMap = new ConcurrentHashMap<>();

    public static Map<ShardingTarget, ShardingProperty> getShardingPropertyMap() {
        return shardingPropertyMap;
    }
}
```
- 샤딩 타겟(`ShardingTarget`)별 설정 정보(`ShardingProperty`)를 관리하기 위한 Map입니다.

<br>

**✅ NotificationShardingConfig**
```java
@Setter
@Configuration
@ConfigurationProperties(prefix = "celfeed.sharding") // [1]
public class NotificationShardingConfig {

    private ShardingProperty notification; // [1]

    @PostConstruct
    public void init() {
        ShardingConfig.getShardingPropertyMap()
                        .put(ShardingTarget.NOTIFICATION, notification); // [2]
    }
}
```
**[1]** yaml 파일에 설정한 `celfeed.sharding.notification` 정보를 매핑해줍니다.

**[2]** `ShardingConfig`에서 정의한 Map에 샤딩 타겟과 설정 정보를 추가합니다.

<br>

**✅ UserHolder**
```java
public class UserHolder {

    private static final ThreadLocal<Context> userContext = new ThreadLocal<>();
    
    public static void setSharding(ShardingTarget target, long shardKey) {
        getUserContext().setSharding(new Sharding(target, shardKey));
    }

    public static void clearSharding() {
        getUserContext().setSharding(null);
    }

    public static Sharding getSharding() {
        return getUserContext().getSharding();
    }

    private static Context getUserContext() {
        Context context = userContext.get();
        if (context == null) {
            context = new Context();
            userContext.set(context);
        }
        return context;
    }

    @Getter
    @Setter
    public static class Context {
        private Sharding sharding;
    }

    @Getter
    @Setter
    public static class Sharding {
        private ShardingTarget target;
        private long shardKey;

        public Sharding(ShardingTarget target, long shardKey) {
            this.target = target;
            this.shardKey = shardKey;
        }
    }
}
```
- `ThreadLocal`을 사용해 API 요청마다 DB 접근 전후에 샤딩 정보가 설정 및 해제되는 임시 저장소입니다.
- 샤딩 정보(`Sharding`)에는 샤딩 타겟과 샤딩 키가 포함됩니다.

<br>

**✅ DataSourceRouter**
```java
public class DataSourceRouter extends AbstractRoutingDataSource { // [1]

    private Map<Integer, String> shardDataSourceNames; // [4]

    @Override // [1]
    public void setTargetDataSources(Map<Object, Object> targetDataSources) { // [2]
        super.setTargetDataSources(targetDataSources); // [3]

        shardDataSourceNames = new HashMap<>();

        for (Object key : targetDataSources.keySet()) {
            String dataSourceName = key.toString();
            int shardNo = Integer.parseInt(dataSourceName.split(" ")[0]);

            shardDataSourceNames.put(shardNo, dataSourceName); // [4]
        }
    }

    @Override // [1]
    protected @Nullable Object determineCurrentLookupKey() { // [5]
        UserHolder.Sharding sharding = UserHolder.getSharding();
        int shardNo = getShardNo(sharding); // [6]

        return shardDataSourceNames.get(shardNo); // [9]
    }

    private int getShardNo(UserHolder.Sharding sharding) {
        if (sharding == null) {
            return 0;
        }

        int shardNo = 0;
        ShardingProperty shardingProperty = ShardingConfig.getShardingPropertyMap().get(sharding.getTarget()); // [7]
		        
        if (shardingProperty.getStrategy() == ShardingStrategy.MODULAR) { // [8]
            shardNo = getShardNoByModular(sharding.getShardKey(), shardingProperty.getMod());
        }

        return shardNo;
    }

    private int getShardNoByModular(long shardKey, int modulus) {
        return (int) (shardKey % modulus);
    }
}
```
**[1]** 다중 데이터소스를 사용하는 환경에서 라우터를 구현하기 위해 `AbstractRoutingDataSource`를 상속받고 `setTargetDataSources()` 메서드와 `determineCurrentLookupKey()` 메서드를 오버라이딩합니다.

**[2]** `NotificationDataSourceConfig`에서 여러 `DataSource` 정보를 넘기면서 호출합니다.

**[3]** 데이터소스 이름을 Key로 갖고 `DataSource` 객체를 Value로 갖는 `targetDataSources`를 저장합니다.

**[4]** 샤드 넘버와 데이터소스 이름을 저장합니다.

**[5]** 데이터소스를 결정해야 하는 시점에 콜백됩니다.

**[6]** `ThreadLocal`에 보관한 샤딩 정보를 통해 샤드 넘버를 얻어옵니다.

**[7]** 샤딩 정보(`UserHolder.Sharding`)에 있는 샤딩 타겟을 통해, 샤딩 타겟별 설정 정보가 담긴 곳(`ShardingConfig.getShardingPropertyMap()`)에서 타겟에 맞는 `ShardingProperty`를 가져옵니다.

**[8]** 모듈러 전략에서는 모듈러 연산의 결과로 타겟 샤드를 결정합니다.

**[9]** 샤드 넘버로 [4]에서 저장한 데이터소스 이름을 가져와 lookup key로 리턴하고, 해당 lookup key는 [3]에서 저장한 `targetDataSources`에서 `DataSource` 객체를 찾을 때 사용됩니다.

<br>

**✅ Sharding**
```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Sharding {
    ShardingTarget target();
}
```
- AOP에서 샤딩이 적용되었는지 확인하기 위한 조건으로 쓰기 위해 애노테이션을 추가합니다.

<br>

**✅ NotificationJpaConfig**

```java
@Configuration
@EnableTransactionManagement
@EnableJpaRepositories( // [2]
        basePackages = "com.xowns.celfeed.repository.notification",
        entityManagerFactoryRef = "notificationEntityManagerFactory",
        transactionManagerRef = "notificationTransactionManager"
)
public class NotificationJpaConfig { // [1]

    @Qualifier("notification")
    @Bean
    @ConfigurationProperties("celfeed.jpa") // [3]
    public JpaProperties notificationJpaProperties() {
        return new JpaProperties();
    }

    @Qualifier("notification")
    @Bean
    public LocalContainerEntityManagerFactoryBean notificationEntityManagerFactory(
            @Qualifier("notification") DataSource dataSource, // [4]
            @Qualifier("notification") JpaProperties jpaProperties
    ) {

        LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();

        factory.setDataSource(dataSource);
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setPackagesToScan("com.xowns.celfeed.domain.notification");
        factory.setPersistenceUnitName("notification");
        factory.setJpaPropertyMap(jpaProperties.getProperties());

        return factory;
    }

    @Qualifier("notification")
    @Bean
    public PlatformTransactionManager notificationTransactionManager(@Qualifier("notification") EntityManagerFactory emf) {
        JpaTransactionManager txManager = new JpaTransactionManager();
        txManager.setEntityManagerFactory(emf);
        return txManager;
    }
}
```
**[1]** 샤딩 처리된 알림 DB에 접근하는 `DataSource`를 사용할 `EntityManagerFactory`를 직접 등록해 줍니다.

**[2]** basePackages에 포함되는 `JpaRepository`는 직접 등록한 `notificationEntityManagerFactory`를 통해 `EntityManager`를 만들고 `notificationTransactionManager`를 통해 트랜잭션을 관리하도록 합니다.

**[3]** application.yml에 설정한 JPA 관련 속성들(hbm2ddl, naming_strategy 등)을 매핑시킵니다.

**[4]** `NotificationDataSourceConfig`에서 등록한 `DataSource`를 주입받습니다.

<br>

**✅ NotificationService**
```java
@Service
@RequiredArgsConstructor
@Transactional(value = "notificationTransactionManager", readOnly = true) // [1]
@Sharding(target = ShardingTarget.NOTIFICATION) // [2]
public class NotificationService {
    ...
}
```
**[1]** 해당 서비스에서는 `NotificationJpaConfig`에서 등록한 `notificationTransactionManager`로 트랜잭션을 관리합니다.

**[2]** Notification DB에 샤딩을 적용하기 위해 `@Sharding` 애노테이션을 target과 함께 추가해 줍니다.

<br>

**✅ ServiceAspect**
```java
@Component
@Aspect
@Order(1) // [5]
public class ServiceAspect {

    // [1]
    @Pointcut("execution(public * com.xowns.celfeed.service.notification..*.*(..))")
    private void service() {
    }

    @Around("service() && @within(sharding) && args(shardKey,..)") // [2]
    public Object handler(ProceedingJoinPoint pjp, Sharding sharding, long shardKey) throws Throwable {																							    
        UserHolder.setSharding(sharding.target(), shardKey); // [3]
        Object returnVal = pjp.proceed();
        UserHolder.clearSharding(); // [4]

        return returnVal;
    }
}
```
**[1]** notification 패키지 하위에서 있는 클래스에 적용합니다.

**[2]** 클래스에 `@Sharding` 애노테이션이 붙어있어야 하며 메서드의 첫 번째 인자가 long 타입이어야 하고, 해당 인자를 샤딩 키로 사용합니다. 즉, 샤딩 키로 사용하기로한 `receiverId`가 첫 번째 인자로 와야 합니다.

**[3]** 메서드 실행 전에 샤딩 타겟과 샤딩 키를 `ThreadLocal`에 보관합니다.

**[4]** `ThreadLocal`을 초기화합니다.

**[5]** Spring AOP는 여러 Aspect가 적용될 경우 실행 순서를 보장하지 않습니다. `@Transactional`이 시작되기 전에 [3]번 과정이 선행되어야 샤딩 정보가 먼저 결정되고, 그 정보를 기반으로 `DataSource`가 선택되기 때문에 `@Order(1)`를 통해 우선순위를 정해줬습니다.

<br>

➡️ 샤딩을 적용하면서 **단일 DB에 집중되던 부하를 분산**시킬 수 있게 되었지만 적용 과정에서 알 수 있다시피 **애플리케이션 복잡도가 눈에 띄게 증가**했습니다.

<br>

## 5. 성능 테스트

> nGrinder를 사용해 알림 DB에 부하를 가하고, **샤딩 적용 전과 후의 성능을 비교**했습니다.
> 
> 또한 DB에 발생하는 부하를 확인하기 위해 Prometheus의 `mysqld_exporter`를 활용했습니다.

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
- 게시글을 작성하면 팔로워 수만큼 알림 데이터가 생성됩니다.
- 팔로워 수가 1만 명인 셀럽이 지속적으로 게시글을 작성하는 상황을 가정했습니다.

<br>

**✅ 샤딩 적용 전**

![issue6-0-3](/docs/img/issue6-0-3.png)

![issue6-0-4](/docs/img/issue6-0-4.png)

- 샤딩 적용 전에는 단일 DB 인스턴스인 상태입니다.
- Test Script처럼 로그인하고 게시글을 작성하면 하나의 DB에서 아래와 같은 과정을 거칩니다.
    - 로그인할 때 member 테이블 `select`
    - 게시글 작성할 때 post 테이블 `insert`
    - 팔로워 정보가져올 때 follow 테이블 `select`
    - 알림 데이터 생성할 때 notification 테이블 `insert`

<br>

**✅ 샤딩 적용 후**

![issue6-0-5](/docs/img/issue6-0-5.png)

![issue6-0-6](/docs/img/issue6-0-6.png) 기존 DB

![issue6-0-7](/docs/img/issue6-0-7.png) 알림 DB 1

![issue6-0-8](/docs/img/issue6-0-8.png) 알림 DB 2

- 기존 DB에서 알림 테이블을 분리했고, 2개의 알림 DB 인스턴스를 띄웠습니다.
- 가장 무거운 작업을 분리해서 부하를 분산시켰습니다.
    - 로그인할 때 member 테이블 `select`
    - 게시글 작성할 때 post 테이블 `insert`
    - 팔로워 정보가져올 때 follow 테이블 `select`
    - 알림 데이터 생성할 때 notification 테이블 `insert` → 무거운 작업 분리

<br>

**✅ 성능 비교**

|  | 샤딩 전 | 샤딩 후 | 비교 |
| :--- | ---: | ---: | ---: |
| **TPS** | 33.5 | 37.2 | **+ 11%** |
| **Mean Test Time** | 3,028.87ms | 2,686.87ms | **- 11%** |
| **QPS** | 363.73 | 145.68 | **- 60%** |
| **Mean Inbound Traffic** | 392kB/s | 23.0kB/s | **- 94%** |
- vuser는 100(5*20)으로 설정하고 3분간 테스트를 진행했습니다.
- 샤딩 적용 후, **기존 DB**에 유의미한 성능 개선을 확인할 수 있습니다.
    - 알림 테이블에 `insert`하는 작업을 다른 DB 인스턴스가 담당하게 되어, QPS(초당 처리되는 쿼리의 수)가 **약 2.5배 감소**했습니다.
    - DB가 수신하는 네트워크 트래픽을 나타내는 Inbound 값은 **약 17배 감소**했습니다.

<br>

**✅ 샤딩 처리된 알림 DB**

|  | 알림 DB 1 | 알림 DB 2 | 평균 |
| :--- | ---: | ---: | ---: |
| **QPS** | 4.47 | 4.07 | 4.27 |
| **Mean Inbound Traffic** | 323kB/s | 319kB/s | 321kB/s |
- 기존 DB의 부하는 줄어들었지만, 그 부하가 샤딩 처리된 알림 DB 각각에 전달되었습니다.

<br>

![issue6-0-9](/docs/img/issue6-0-9.png)

➡️ 샤딩을 적용하면서 **알림 트래픽이 별도 인스턴스로 분리**되어, 기존 **단일 DB 인스턴스의 부하를 효과적으로 분산**할 수 있게 되었습니다.

<br><br>

[뒤로가기](https://github.com/whxownss/celfeed#4-%EC%95%8C%EB%A6%BC-%EC%84%9C%EB%B2%84%EC%9D%98-%EB%B0%9C%EC%A0%84)

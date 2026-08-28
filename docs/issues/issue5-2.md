# [Spring Batch] 대량의 알림 데이터 이관

<br>

## 1. 개요

> [알림 테이블에 파티션을 적용](/docs/issues/issue5-0.md)함으로써, 사용자에게 더 이상 노출되지 않는 (30일이 지난) 알림 데이터 삭제 성능을 향상시켰습니다.
> 
> 하지만 새로운 요구사항으로 해당 데이터가 필요할 수도 있기 때문에, 단순 삭제보다는 아카이브 DB에 별도로 보관하는 방식이 더 적절합니다.

<br>

## 2. Spring Batch 적용

> Spring Batch를 통해 대량의 알림 데이터를 아카이브 DB로 이관해 보겠습니다.

<br>

**✅ application.yml**
```yaml
spring:
  datasource-batch-meta: # [1]
    jdbc-url: jdbc:mysql://localhost:3306/celfeed_batch_meta
    username: root
    password: root
    driver-class-name: com.mysql.cj.jdbc.Driver
    maximum-pool-size: 2
    minimum-idle: 2
  datasource-batch-data: # [2]
    jdbc-url: jdbc:mysql://localhost:3306/celfeed_batch_data
    username: root
    password: root
    driver-class-name: com.mysql.cj.jdbc.Driver
    maximum-pool-size: 1
    minimum-idle: 1
```
**[1]** 배치 실행 상태와 정보 등의 메타데이터를 저장하는 DB입니다. 해당 DB의 테이블은 Spring Batch에서 제공하는 `org.springframework.batch.core.schema-mysql.sql`를 통해 생성했습니다.

**[2]** 30일이 지난 알림 데이터를 저장할 아카이브 DB입니다.

<br>

**✅ DataSourceConfig**
```java
@Configuration
public class DataSourceConfig {
    ...
    @Qualifier("batchData")
    @Bean
    @ConfigurationProperties(prefix = "spring.datasource-batch-data")
    public DataSource batchDataDataSource() {
        return DataSourceBuilder.create().build();
    }

    @Qualifier("batchMeta")
    @Bean
    @ConfigurationProperties(prefix = "spring.datasource-batch-meta")
    public DataSource batchMetaDataSource() {
        return DataSourceBuilder.create().build();
    }

    @Qualifier("batchMeta")
    @Bean
    public PlatformTransactionManager batchMetaTransactionManager(@Qualifier("batchMeta") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }
}
```
- `application.yml`에 추가한 `DataSource` 정보를 매핑하여 빈으로 등록합니다.

<br>

**✅ BatchConfig**
```java
@Configuration
@DependsOn("batchMetaDataSource")
public class BatchConfig extends JdbcDefaultBatchConfiguration { // [1]

    private final DataSource dataSource;
    private final PlatformTransactionManager transactionManager;

    public BatchConfig( // [2]
            @Qualifier("batchMeta") DataSource dataSource,
            @Qualifier("batchMeta") PlatformTransactionManager transactionManager
    ) {
        this.dataSource = dataSource;
        this.transactionManager = transactionManager;
    }

    @Override
    protected DataSource getDataSource() { // [3]
        return dataSource;
    }

    @Override
    protected PlatformTransactionManager getTransactionManager() { // [3]
        return transactionManager;
    }
}
```
**[1]** Spring Batch 구성을 직접 설정하기 위해 `JdbcDefaultBatchConfiguration`를 상속받습니다.

**[2]** 메타데이터 DB에 접근하는 `DataSource`와 해당 DB의 트랜잭션을 관리하는 `TransactionManager` 를 생성자 주입 방식으로 주입받습니다.

**[3]** `JobRepository`를 생성할 때 호출될 메서드들로 생성자를 통해 주입받은 객체를 반환하도록 합니다.

<br>

✅ **DataMigrationJobConfig - Job**
```java
@Configuration
public class DataMigrationJobConfig {
    private static final int CHUNK_SIZE = 1000;
    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;

    public DataMigrationJobConfig(
            JobRepository jobRepository,
            @Qualifier("batchMeta") PlatformTransactionManager transactionManager
    ) {
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
    }

    @Bean
    public Job dataMigrationJob(Step dataMigrationStep) { // [1]
        return new JobBuilder("dataMigrationJob", jobRepository)
                .start(dataMigrationStep)
                .next(dataMigrationStep) // [2]
                .build();
    }
    ...
}
```
**[1]** `Job`에 대한 정의로, 이어서 설명할 `Step`으로 구성되어 있습니다.

**[2]** 샤딩을 적용하면서 추가하게 되었고, 같은 `Step`을 샤딩 처리된 알림 DB 1, 2에 각각 적용합니다.

<br>

✅ **DataMigrationJobConfig - Step**
```java
@Configuration
public class DataMigrationJobConfig {
    private static final int CHUNK_SIZE = 1000;
    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    ...
    @Bean
    public Step dataMigrationStep( // [1]
            ItemReader<Notification> itemReader,
            ItemWriter<Notification> itemWriter
    ) {
        return new StepBuilder("dataMigrationStep", jobRepository)
                .<Notification, Notification> chunk(CHUNK_SIZE)
                .transactionManager(transactionManager)
                .reader(itemReader)
                .writer(itemWriter)
                .listener(new StepExecutionListener() { // [2]
                    @Override
                    public void beforeStep(StepExecution stepExecution) {
                        UserHolder.setSharding(
		                        ShardingTarget.NOTIFICATION, stepExecution.getId() % 2
                        );
                    }

                    @Override
                    public ExitStatus afterStep(StepExecution stepExecution) {
                        UserHolder.clearSharding();
                        return StepExecutionListener.super.afterStep(stepExecution);
                    }
                })
                .build();
    }
    ...
    @Getter
    @AllArgsConstructor
    public static class Notification {
        private Long id;
        private Long receiverId;
        private Long actorId;
        private String type;
        private Long targetId;
        private String isRead;
        private Timestamp createdAt;
    }
}
```
**[1]** `Step`에 대한 정의로 이어서 설명할 `ItemReader`와 `ItemWriter`로 구성되어 있습니다.

**[2]** 샤딩을 적용하면서 추가하게 되었고, 해당 `Step`을 실행하기 전후로 `ThreadLocal`에 샤딩 키 설정 및 해제 작업을 합니다.

<br>

✅ **DataMigrationJobConfig - ItemReader, ItemWriter**
```java
@Configuration
public class DataMigrationJobConfig {
    private static final int CHUNK_SIZE = 1000;
    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    ...
    @Bean
    @StepScope
    public JdbcPagingItemReader<Notification> itemReader(
            @Qualifier("notification") DataSource dataSource, // [1]
            @Value("#{jobParameters['date']}") String date, // [2]
            PagingQueryProvider queryProvider
    ) throws Exception {

        Map<String, Object> parameterValues = new HashMap<>();
        parameterValues.put("date", date);

        return new JdbcPagingItemReaderBuilder<Notification>()
                .name("itemReader")
                .dataSource(dataSource) // [1]
                .queryProvider(queryProvider)
                .parameterValues(parameterValues)
                .rowMapper((rs, rowNum) -> new Notification(
                        rs.getLong(1),
                        rs.getLong(2),
                        rs.getLong(3),
                        rs.getString(4),
                        rs.getLong(5),
                        rs.getString(6),
                        rs.getTimestamp(7)
                ))
                .pageSize(CHUNK_SIZE)
                .build();
    }

    @Bean
    public SqlPagingQueryProviderFactoryBean queryProvider(@Qualifier("notification")DataSource dataSource) {
        SqlPagingQueryProviderFactoryBean provider = new SqlPagingQueryProviderFactoryBean();

        provider.setDataSource(dataSource);
        provider.setSelectClause("select id, receiver_id, actor_id, type, target_id, is_read, created_at");
        provider.setFromClause("from notification");
        provider.setWhereClause("created_at < :date"); // [2]
        provider.setSortKey("id");

        return provider;
    }

    @Bean
    public JdbcBatchItemWriter<Notification> itemWriter(
            @Qualifier("batchData") DataSource dataSource // [3]
    ) {
        return new JdbcBatchItemWriterBuilder<Notification>()
                .dataSource(dataSource) // [3]
                .sql(
                        "insert into notification(id, receiver_id, actor_id, type, target_id, is_read, created_at) " +
                                "values(:id, :receiverId, :actorId, :type, :targetId, :isRead, :createdAt)"
                )
                .beanMapped()
                .build();
    }
    ...
}
```
**[1]** `ItemReader`에서는 알림 테이블이 존재하는 DB에 접근하는 `DataSource`를 사용합니다.

**[2]** `Job`이 실행되는 시점에 넘겨받는 파라미터로, 이관할 데이터를 조회할 때 where 절에 사용됩니다.

**[3]** `ItemWriter`에서는 이관 데이터를 저장할 아카이브 DB에 접근하는 `DataSource`를 사용합니다.

<br>

**✅ JobScheduleConfig**
```java
@Configuration
@RequiredArgsConstructor
public class JobScheduleConfig { // [1]

    private final JobOperator jobOperator; // [2]
    private final Job job;

    @Scheduled(cron = "0 0 4 3 * *", zone = "Asia/Seoul") // [3]
    public void runJob() throws Exception {
        LocalDate firstDayOfLastMonth = LocalDate.now()
                .minusMonths(1)
                .withDayOfMonth(1); // [3]

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");
        String date = firstDayOfLastMonth.format(formatter);

        JobParameters param = new JobParametersBuilder()
                .addString("date", date)
                .toJobParameters();

        jobOperator.start(job, param);
    }
}
```
**[1]** 앞서 설정한 `Job`을 스케줄 작업으로 실행하기 위한 설정 파일입니다.

**[2]** `JobOperator`는 `Job`의 실행을 관리합니다.

**[3]** 매월 3일 04시가 되면, 지난달 1일 이전 데이터들의 이관 작업을 실행하도록 합니다. 예를 들어 3월 3일에는 2월 1일 이전 데이터들을 이관합니다. 만약 매달 1일이나 2일에 실행하게 될 경우, 2월에는 해당 날짜 기준으로 1월 데이터도 조회가 되어야 하기 때문에 매달 3일에 스케줄 작업을 실행합니다.

<br>

➡️ **Spring Batch**를 활용해 더 이상 사용자에게 노출되지 않는 데이터를 **아카이브 DB에 이관**하도록 구축했습니다. 이를 통해 **운영 DB에는 사용자에게 제공되는 데이터만 유지하도록 분리했으며,** 30일이 지난 데이터가 필요한 경우 **아카이브 DB를 통해 조회**할 수 있도록 했습니다.

<br><br>

[뒤로가기](https://github.com/whxownss/celfeed/tree/docs-readme#4-%EC%95%8C%EB%A6%BC-%EC%84%9C%EB%B2%84%EC%9D%98-%EB%B0%9C%EC%A0%84)

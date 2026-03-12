package com.xowns.celfeed.config.batch;

import com.xowns.celfeed.config.sharding.ShardingTarget;
import com.xowns.celfeed.config.sharding.UserHolder;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.ItemReader;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.batch.infrastructure.item.database.JdbcBatchItemWriter;
import org.springframework.batch.infrastructure.item.database.JdbcPagingItemReader;
import org.springframework.batch.infrastructure.item.database.PagingQueryProvider;
import org.springframework.batch.infrastructure.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.infrastructure.item.database.builder.JdbcPagingItemReaderBuilder;
import org.springframework.batch.infrastructure.item.database.support.SqlPagingQueryProviderFactoryBean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;

@Configuration
public class DataMigrationJobConfig {

    private static final int CHUNK_SIZE = 1000;
    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;

    public DataMigrationJobConfig(JobRepository jobRepository,
                                  @Qualifier("batchMeta") PlatformTransactionManager transactionManager) {

        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
    }

    @Bean
    public Job dataMigrationJob(Step dataMigrationStep) {
        return new JobBuilder("dataMigrationJob", jobRepository)
                .start(dataMigrationStep)
                .next(dataMigrationStep)
                .build();
    }

    @Bean
    public Step dataMigrationStep(ItemReader<Notification> itemReader, ItemWriter<Notification> itemWriter) {
        return new StepBuilder("dataMigrationStep", jobRepository)
                .<Notification, Notification> chunk(CHUNK_SIZE)
                .transactionManager(transactionManager)
                .reader(itemReader)
                .writer(itemWriter)
                .listener(new StepExecutionListener() {
                    @Override
                    public void beforeStep(StepExecution stepExecution) {
                        UserHolder.setSharding(ShardingTarget.NOTIFICATION, stepExecution.getId() % 2);
                    }

                    @Override
                    public @Nullable ExitStatus afterStep(StepExecution stepExecution) {
                        UserHolder.clearSharding();
                        return StepExecutionListener.super.afterStep(stepExecution);
                    }
                })
                .build();
    }

    @Bean
    @StepScope
    public JdbcPagingItemReader<Notification> itemReader(@Qualifier("notification")DataSource dataSource,
                                                         @Value("#{jobParameters['date']}") String date,
                                                         PagingQueryProvider queryProvider) throws Exception {

        Map<String, Object> parameterValues = new HashMap<>();
        parameterValues.put("date", date);

        return new JdbcPagingItemReaderBuilder<Notification>()
                .name("itemReader")
                .dataSource(dataSource)
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
        provider.setWhereClause("created_at < :date");
        provider.setSortKey("id");

        return provider;
    }

    @Bean
    public JdbcBatchItemWriter<Notification> itemWriter(@Qualifier("batchData") DataSource dataSource) {
        return new JdbcBatchItemWriterBuilder<Notification>()
                .dataSource(dataSource)
                .sql(
                        "insert into notification(id, receiver_id, actor_id, type, target_id, is_read, created_at) " +
                                "values(:id, :receiverId, :actorId, :type, :targetId, :isRead, :createdAt)"
                )
                .beanMapped()
                .build();
    }


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

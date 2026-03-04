package com.xowns.celfeed.config.batch;

import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;


@Configuration
public class DataMigrationJobConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;

    public DataMigrationJobConfig(JobRepository jobRepository,
                                  @Qualifier("batchMeta") PlatformTransactionManager transactionManager) {

        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
    }

}

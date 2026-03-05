package com.xowns.celfeed.config.datasource;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

@Configuration
public class DataSourceConfig {

    @Qualifier("basic")
    @Bean
    @ConfigurationProperties(prefix = "spring.datasource-celfeed")
    public DataSource dataSource() {
        return DataSourceBuilder.create().build();
    }

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

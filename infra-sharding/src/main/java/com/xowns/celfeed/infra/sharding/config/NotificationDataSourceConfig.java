package com.xowns.celfeed.infra.sharding.config;

import com.xowns.celfeed.infra.sharding.core.ShardingDataSourceProperty;
import com.xowns.celfeed.infra.sharding.router.DataSourceRouter;
import com.zaxxer.hikari.HikariDataSource;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;

import javax.sql.DataSource;
import java.util.LinkedHashMap;
import java.util.Map;

@Setter
@Configuration
@ConfigurationProperties(prefix = "celfeed.datasource")
public class NotificationDataSourceConfig {

    private ShardingDataSourceProperty notification;

    @Qualifier("notification")
    @Bean(defaultCandidate = false)
    public DataSource notificationDataSource() {
        DataSourceRouter router = new DataSourceRouter();
        Map<Object, Object> dataSourceMap = new LinkedHashMap<>();

        for (ShardingDataSourceProperty.Shard shard : notification.getShards()) {
            DataSource dataSource = dataSource(shard.getUsername(), shard.getPassword(), shard.getUrl());
            dataSourceMap.put(shard.getShardNo() + " " + shard.getName(), dataSource);
        }

        router.setTargetDataSources(dataSourceMap);
        router.afterPropertiesSet();

        return new LazyConnectionDataSourceProxy(router);
    }

    private DataSource dataSource(String username, String password, String url) {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(url);
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");

        dataSource.setMaximumPoolSize(10);
        dataSource.setMinimumIdle(10);
        dataSource.setConnectionTimeout(3000);

        return dataSource;
    }

    @Qualifier("notification")
    @Bean(defaultCandidate = false)
    public JdbcTemplate notificationJdbcTemplate(@Qualifier("notification") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}

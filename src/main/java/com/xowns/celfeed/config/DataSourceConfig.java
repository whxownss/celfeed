package com.xowns.celfeed.config;

import com.xowns.celfeed.config.sharding.DataSourceRouter;
import com.xowns.celfeed.config.sharding.ShardingDataSourceProperty;
import com.zaxxer.hikari.HikariDataSource;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;

import javax.sql.DataSource;
import java.util.LinkedHashMap;
import java.util.Map;

@Setter
@Configuration
@ConfigurationProperties(prefix = "celfeed.datasource")
public class DataSourceConfig {

    private ShardingDataSourceProperty notification;

    @Qualifier("notification")
    @Bean(defaultCandidate = false)
    public DataSource notificationDataSource() {
        DataSourceRouter router = new DataSourceRouter();
        Map<Object, Object> dataSourceMap = new LinkedHashMap<>();

        for (int i = 0; i < notification.getShards().size(); i++) {
            ShardingDataSourceProperty.Shard shard = notification.getShards().get(i);

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
}

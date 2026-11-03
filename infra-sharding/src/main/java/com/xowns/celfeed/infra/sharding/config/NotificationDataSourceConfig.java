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
@ConfigurationProperties(prefix = "app.datasource")
public class NotificationDataSourceConfig {

    private ShardingDataSourceProperty notification;

    @Bean
    public DataSource notificationDataSource() {
        DataSourceRouter router = new DataSourceRouter();
        Map<Object, Object> dataSourceMap = new LinkedHashMap<>();

        for (ShardingDataSourceProperty.Shard shard : notification.getShards()) {
            DataSource dataSource = dataSource(
                    shard.getUrl(),
                    shard.getUsername(),
                    shard.getPassword(),
                    shard.getDriverName(),
                    shard.getMaxPool(),
                    shard.getMinIdle(),
                    shard.getTimeout()
            );

            dataSourceMap.put(shard.getShardNo() + " " + shard.getName(), dataSource);
        }

        router.setTargetDataSources(dataSourceMap);
        router.afterPropertiesSet();

        return new LazyConnectionDataSourceProxy(router);
    }

    private DataSource dataSource(String url, String username, String password, String driverName,
                                  int maxPool, int minIdle, int timeout) {

        HikariDataSource dataSource = new HikariDataSource();

        dataSource.setJdbcUrl(url);
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        dataSource.setDriverClassName(driverName);

        dataSource.setMaximumPoolSize(maxPool);
        dataSource.setMinimumIdle(minIdle);
        dataSource.setConnectionTimeout(timeout);

        return dataSource;
    }

    @Bean
    public JdbcTemplate notificationJdbcTemplate(@Qualifier("notificationDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}

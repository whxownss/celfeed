package com.xowns.celfeed.config.sharding;

import jakarta.annotation.PostConstruct;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "celfeed.sharding")
@Setter
public class NotificationShardingConfig {

    private ShardingProperty notification;

    @PostConstruct
    public void init() {
        ShardingConfig.getShardingPropertyMap()
                .put(ShardingTarget.NOTIFICATION, notification);
    }
}

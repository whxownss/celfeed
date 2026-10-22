package com.xowns.celfeed.sharding.config;

import com.xowns.celfeed.sharding.core.ShardingProperty;
import com.xowns.celfeed.sharding.core.ShardingPropertyMap;
import com.xowns.celfeed.sharding.core.ShardingTarget;
import jakarta.annotation.PostConstruct;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "celfeed.sharding")
@Setter
public class ShardingPropertyInitializer {

    private ShardingProperty notification;

    @PostConstruct
    public void init() {
        ShardingPropertyMap.get()
                .put(ShardingTarget.NOTIFICATION, notification);
    }
}

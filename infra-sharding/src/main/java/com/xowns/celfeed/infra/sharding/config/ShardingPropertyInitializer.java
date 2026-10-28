package com.xowns.celfeed.infra.sharding.config;

import com.xowns.celfeed.infra.sharding.core.ShardingProperty;
import com.xowns.celfeed.infra.sharding.core.ShardingPropertyMap;
import com.xowns.celfeed.infra.sharding.core.ShardingTarget;
import jakarta.annotation.PostConstruct;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.sharding")
@Setter
public class ShardingPropertyInitializer {

    private ShardingProperty notification;

    @PostConstruct
    public void init() {
        ShardingPropertyMap.get()
                .put(ShardingTarget.NOTIFICATION, notification);
    }
}

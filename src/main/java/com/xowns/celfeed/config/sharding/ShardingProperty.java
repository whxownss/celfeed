package com.xowns.celfeed.config.sharding;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class ShardingProperty {
    private ShardingStrategy strategy;
    private int mod;
    private String shardingKey;
}

package com.xowns.celfeed.sharding.core;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class ShardingProperty {
    private ShardingStrategy strategy;
    private int mod;
    private String shardingKey;
}

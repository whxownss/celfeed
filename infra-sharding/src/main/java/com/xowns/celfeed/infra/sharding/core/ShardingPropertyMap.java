package com.xowns.celfeed.infra.sharding.core;

import lombok.Setter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Setter
public abstract class ShardingPropertyMap {
    private static Map<ShardingTarget, ShardingProperty> shardingPropertyMap = new ConcurrentHashMap<>();

    public static Map<ShardingTarget, ShardingProperty> get() {
        return shardingPropertyMap;
    }
}

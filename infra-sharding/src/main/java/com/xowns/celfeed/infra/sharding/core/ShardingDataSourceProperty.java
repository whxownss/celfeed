package com.xowns.celfeed.infra.sharding.core;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter @Setter
public class ShardingDataSourceProperty {

    private List<Shard> shards;

    @Getter @Setter
    public static class Shard {
        // 샤딩 정보
        private int shardNo;
        private String name;

        // DB 정보
        private String url;
        private String username;
        private String password;
        private String driverName;

        // Hikari
        private int maxPool;
        private int minIdle;
        private int timeout;
    }
}

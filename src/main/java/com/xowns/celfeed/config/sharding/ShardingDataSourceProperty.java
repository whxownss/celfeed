package com.xowns.celfeed.config.sharding;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter @Setter
public class ShardingDataSourceProperty {

    private List<Shard> shards;

    @Getter @Setter
    public static class Shard {
        private int shardNo;
        private String name;
        private String username;
        private String password;
        private String url;
    }
}

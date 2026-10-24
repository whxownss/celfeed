package com.xowns.celfeed.infra.sharding.router;

import com.xowns.celfeed.infra.sharding.holder.UserHolder;
import com.xowns.celfeed.infra.sharding.core.ShardingProperty;
import com.xowns.celfeed.infra.sharding.core.ShardingPropertyMap;
import com.xowns.celfeed.infra.sharding.core.ShardingStrategy;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

import java.util.HashMap;
import java.util.Map;

public class DataSourceRouter extends AbstractRoutingDataSource {

    private Map<Integer, String> shardDataSourceNames;

    @Override
    public void setTargetDataSources(Map<Object, Object> targetDataSources) {
        super.setTargetDataSources(targetDataSources);

        shardDataSourceNames = new HashMap<>();

        for (Object key : targetDataSources.keySet()) {
            String dataSourceName = key.toString();
            String shardNoStr = dataSourceName.split(" ")[0];

            shardDataSourceNames.put(Integer.parseInt(shardNoStr), dataSourceName);
        }
    }

    @Override
    protected @Nullable Object determineCurrentLookupKey() {
        UserHolder.Sharding sharding = UserHolder.getSharding();
        int shardNo = getShardNo(sharding);

        return shardDataSourceNames.get(shardNo);
    }

    private int getShardNo(UserHolder.Sharding sharding) {
        if (sharding == null) {
            return 0;
        }

        int shardNo = 0;
        ShardingProperty shardingProperty = ShardingPropertyMap.get().get(sharding.getTarget());
        if (shardingProperty.getStrategy() == ShardingStrategy.MODULAR) {
            shardNo = getShardNoByModular(sharding.getShardKey(), shardingProperty.getMod());
        }

        return shardNo;
    }

    private int getShardNoByModular(long shardKey, int modulus) {
        return (int) (shardKey % modulus);
    }
}

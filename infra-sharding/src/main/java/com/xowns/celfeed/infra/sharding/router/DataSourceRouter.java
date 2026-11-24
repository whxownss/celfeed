package com.xowns.celfeed.infra.sharding.router;

import com.xowns.celfeed.infra.sharding.holder.UserHolder;
import com.xowns.celfeed.infra.sharding.core.ShardingProperty;
import com.xowns.celfeed.infra.sharding.core.ShardingPropertyMap;
import com.xowns.celfeed.infra.sharding.core.ShardingStrategy;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

import java.util.HashMap;
import java.util.Map;

@Slf4j
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

        if (sharding == null) {
            throw new IllegalStateException("[DataSourceRouter] 샤딩 정보 없이 DB 접근");
        }

        int shardNo = getShardNo(sharding);
        String key = shardDataSourceNames.get(shardNo);

        if (key == null) {
            throw new IllegalStateException("[DataSourceRouter] 존재하지 않는 샤드 : " + shardNo);
        }

        return key;
    }

    private int getShardNo(UserHolder.Sharding sharding) {
        ShardingProperty property = ShardingPropertyMap.get().get(sharding.getTarget());

        if (property == null) {
            throw new IllegalStateException("[DataSourceRouter] 지원하지 않는 샤딩 설정 : " + sharding.getTarget());
        }

        if (property.getStrategy() == ShardingStrategy.MODULAR) {
            int shardNo = getShardNoByModular(sharding.getShardKey(), property.getMod());
            log.info("[Notification DataSourceRouter] 샤딩 정보 : 전략=MODULAR, 샤드번호={}", shardNo);
            return shardNo;
        }

        throw new IllegalStateException("[DataSourceRouter] 지원하지 않는 샤딩 전략 : " + property.getStrategy());
    }

    private int getShardNoByModular(long shardKey, int modulus) {
        return (int) (shardKey % modulus);
    }
}

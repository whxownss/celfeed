package com.xowns.celfeed.config.sharding;

import lombok.Getter;
import lombok.Setter;

public class UserHolder {

    private static final ThreadLocal<Context> userContext = new ThreadLocal<>();

    public static void setSharding(ShardingTarget target, long shardKey) {
        getUserContext().setSharding(new Sharding(target, shardKey));
    }

    public static void clearSharding() {
        getUserContext().setSharding(null);
    }

    public static Sharding getSharding() {
        return getUserContext().getSharding();
    }

    private static Context getUserContext() {
        Context context = userContext.get();
        if (context == null) {
            context = new Context();
            userContext.set(context);
        }
        return context;
    }

    @Getter @Setter
    public static class Context {
        private Sharding sharding;
    }

    @Getter @Setter
    public static class Sharding {
        private ShardingTarget target;
        private long shardKey;

        public Sharding(ShardingTarget target, long shardKey) {
            this.target = target;
            this.shardKey = shardKey;
        }
    }
}

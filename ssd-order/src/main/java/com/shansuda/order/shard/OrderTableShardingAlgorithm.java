package com.shansuda.order.shard;

import org.apache.shardingsphere.sharding.api.sharding.standard.PreciseShardingValue;
import org.apache.shardingsphere.sharding.api.sharding.standard.RangeShardingValue;
import org.apache.shardingsphere.sharding.api.sharding.standard.StandardShardingAlgorithm;

import java.util.Collection;
import java.util.Properties;

public final class OrderTableShardingAlgorithm implements StandardShardingAlgorithm<Comparable<?>> {

    @Override
    public void init(Properties props) {
    }

    @Override
    public String doSharding(Collection<String> availableTargetNames, PreciseShardingValue<Comparable<?>> shardingValue) {
        long id = ((Number) shardingValue.getValue()).longValue();
        String target = OrderSharding.table(id);
        if (!availableTargetNames.contains(target)) {
            throw new IllegalArgumentException("未知表 " + target);
        }
        return target;
    }

    @Override
    public Collection<String> doSharding(Collection<String> availableTargetNames, RangeShardingValue<Comparable<?>> shardingValue) {
        return availableTargetNames;
    }

    @Override
    public String getType() {
        return "SSD_ORDER_TABLE";
    }
}

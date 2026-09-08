package com.shansuda.order.shard;

public final class OrderSharding {

    private OrderSharding() {
    }

    public static int dbIndex(long orderId) {
        return (int) (orderId % 4);
    }

    public static int tableIndex(long orderId) {
        return (int) ((orderId / 4) % 8);
    }

    public static String dataSource(long orderId) {
        return "ds" + dbIndex(orderId);
    }

    public static String table(long orderId) {
        return "t_order_" + tableIndex(orderId);
    }
}

package com.shansuda.activity.service;

/** 活动幂等键：服务端拼装，禁止客户端当权威。秒杀必须带当前 SKU。 */
public final class ActivityKeys {

    private ActivityKeys() {
    }

    public static String seckill(long activityId, long userId, long skuId) {
        return activityId + ":" + userId + ":SECKILL:" + skuId;
    }

    public static String coupon(long activityId, long userId) {
        return activityId + ":" + userId + ":COUPON";
    }
}

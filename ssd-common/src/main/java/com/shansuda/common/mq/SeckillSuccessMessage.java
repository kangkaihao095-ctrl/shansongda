package com.shansuda.common.mq;

public record SeckillSuccessMessage(
        String idempotencyKey,
        long userId,
        long activityId,
        long skuId,
        String skuName,
        int priceCents,
        long merchantId
) {
}

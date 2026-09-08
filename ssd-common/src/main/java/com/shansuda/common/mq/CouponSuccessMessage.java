package com.shansuda.common.mq;

public record CouponSuccessMessage(
        String idempotencyKey,
        long activityId,
        long userId,
        String scene,
        String couponCode
) {
}

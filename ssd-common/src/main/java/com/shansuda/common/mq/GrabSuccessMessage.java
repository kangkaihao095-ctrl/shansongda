package com.shansuda.common.mq;

public record GrabSuccessMessage(
        String idempotencyKey,
        long orderId,
        long riderId
) {
}

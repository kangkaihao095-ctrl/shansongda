package com.shansuda.common.mq;

public record OrderPaidMessage(long orderId, long userId, long merchantId) {
}

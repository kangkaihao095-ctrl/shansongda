package com.shansuda.common.mq;

public final class MqNames {

    public static final String ACTIVITY_EXCHANGE = "ssd.activity";
    public static final String ORDER_EXCHANGE = "ssd.order";

    public static final String SECKILL_SUCCESS = "seckill.success";
    public static final String COUPON_SUCCESS = "coupon.success";
    public static final String GRAB_SUCCESS = "grab.success";
    public static final String ORDER_PAID = "order.paid";

    public static final String Q_SECKILL = "ssd.activity.seckill";
    public static final String Q_COUPON = "ssd.activity.coupon";
    public static final String Q_GRAB = "ssd.activity.grab";
    public static final String Q_ORDER_PAID = "ssd.order.paid";

    private MqNames() {
    }
}

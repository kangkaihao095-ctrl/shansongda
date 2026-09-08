package com.shansuda.order.mq;

import com.shansuda.common.mq.GrabSuccessMessage;
import com.shansuda.common.mq.MqNames;
import com.shansuda.common.mq.OrderPaidMessage;
import com.shansuda.common.mq.SeckillSuccessMessage;
import com.shansuda.order.service.OrderService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class OrderEventListener {

    private final OrderService orderService;

    public OrderEventListener(OrderService orderService) {
        this.orderService = orderService;
    }

    @RabbitListener(queues = MqNames.Q_SECKILL)
    public void onSeckill(SeckillSuccessMessage msg) {
        orderService.createFromSeckill(msg);
    }

    @RabbitListener(queues = MqNames.Q_GRAB)
    public void onGrab(GrabSuccessMessage msg) {
        orderService.acceptGrab(msg);
    }

    @RabbitListener(queues = MqNames.Q_ORDER_PAID)
    public void onPaid(OrderPaidMessage msg) {
        orderService.tryAutoAccept(msg.orderId());
    }
}

package com.shansuda.order;

import com.shansuda.common.api.BizException;
import com.shansuda.order.fulfill.OrderStateMachine;
import com.shansuda.order.leaf.LeafAllocator;
import com.shansuda.order.shard.OrderSharding;
import com.shansuda.order.strategy.PenaltyStrategy;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderDomainTest {

    @Test
    void illegalTransitions() {
        assertThrows(BizException.class, () -> OrderStateMachine.deliver("CREATED"));
        assertThrows(BizException.class, () -> OrderStateMachine.pay("PAID"));
        assertThrows(BizException.class, () -> OrderStateMachine.cancel("DELIVERING"));
        assertThrows(BizException.class, () -> OrderStateMachine.cancel("COMPLETED"));
        assertEquals("MERCHANT_PENDING", OrderStateMachine.pay("CREATED"));
        assertEquals("PAID", OrderStateMachine.merchantAccept("MERCHANT_PENDING"));
        assertEquals("ACCEPTED", OrderStateMachine.accept("PAID"));
        assertEquals("ARRIVED", OrderStateMachine.arrive("ACCEPTED"));
        assertEquals("DELIVERING", OrderStateMachine.deliver("ARRIVED"));
        assertEquals("DELIVERING", OrderStateMachine.deliver("ACCEPTED"));
        assertEquals("COMPLETED", OrderStateMachine.complete("DELIVERING"));
        assertEquals("CANCELLING", OrderStateMachine.applyCancel("CREATED"));
        assertEquals("CANCELLED", OrderStateMachine.confirmCancel("CANCELLING"));
        assertEquals("REFUNDING", OrderStateMachine.applyRefund("PAID"));
        assertEquals("REFUNDED", OrderStateMachine.approveRefund("REFUNDING"));
        assertEquals("REFUND_REJECTED", OrderStateMachine.rejectRefund("REFUNDING"));
        assertEquals("REFUNDED", OrderStateMachine.approveRefund(OrderStateMachine.applyRefund("MERCHANT_PENDING")));
        assertThrows(BizException.class, () -> OrderStateMachine.applyRefund("CREATED"));
        assertThrows(BizException.class, () -> OrderStateMachine.cancel("PAID"));
    }

    @Test
    void penaltyByStatus() {
        assertEquals(0, PenaltyStrategy.penaltyCents("CREATED", 600));
        assertEquals(200, PenaltyStrategy.penaltyCents("PAID", 600));
        assertEquals(200, PenaltyStrategy.penaltyCents("MERCHANT_PENDING", 600));
        assertEquals(600, PenaltyStrategy.penaltyCents("ACCEPTED", 600));
        assertEquals(600, PenaltyStrategy.refundPenaltyCents("DELIVERING", 600));
        assertThrows(BizException.class, () -> PenaltyStrategy.penaltyCents("DELIVERING", 600));
    }

    @Test
    void shardingStable() {
        long id = 10027L;
        assertEquals("ds" + (id % 4), OrderSharding.dataSource(id));
        assertEquals("t_order_" + ((id / 4) % 8), OrderSharding.table(id));
        assertEquals(OrderSharding.dataSource(id), OrderSharding.dataSource(id));
        assertEquals(OrderSharding.table(id), OrderSharding.table(id));
        assertEquals(0, OrderSharding.dbIndex(4));
        assertEquals(1, OrderSharding.tableIndex(4));
    }

    @Test
    void leafIdsUnique() {
        AtomicLong max = new AtomicLong(0);
        LeafAllocator leaf = new LeafAllocator(() -> {
            long nextMax = max.addAndGet(1000);
            return LeafAllocator.segment(nextMax - 999, nextMax);
        });
        Set<Long> ids = new HashSet<>();
        for (int i = 0; i < 2500; i++) {
            ids.add(leaf.nextId());
        }
        assertEquals(2500, ids.size());
    }
}

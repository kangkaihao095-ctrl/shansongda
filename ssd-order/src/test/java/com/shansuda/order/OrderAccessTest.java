package com.shansuda.order;

import com.shansuda.common.auth.AuthUser;
import com.shansuda.order.domain.DeliveryOrder;
import com.shansuda.order.fulfill.OrderAccess;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderAccessTest {

    @Test
    void ownerAndMerchantCanView() {
        DeliveryOrder order = order(1, 3, null, "CREATED");
        assertTrue(OrderAccess.canView(new AuthUser(1, "USER", null), order));
        assertTrue(OrderAccess.canView(new AuthUser(3, "MERCHANT", 3L), order));
        assertFalse(OrderAccess.canView(new AuthUser(9, "USER", null), order));
    }

    @Test
    void otherRiderCannotViewOrPayForeignOrder() {
        DeliveryOrder accepted = order(1, 3, 2L, "ACCEPTED");
        AuthUser otherRider = new AuthUser(8, "RIDER", 8L);
        assertFalse(OrderAccess.canView(otherRider, accepted));
        assertFalse(OrderAccess.canPayOrCancel(otherRider, accepted));
        assertTrue(OrderAccess.canView(new AuthUser(2, "RIDER", 2L), accepted));
        assertTrue(OrderAccess.canPayOrCancel(new AuthUser(1, "USER", null), accepted));
    }

    @Test
    void idleRiderCanSeeUnassignedPaidHall() {
        DeliveryOrder paid = order(1, 3, null, "PAID");
        assertTrue(OrderAccess.canView(new AuthUser(2, "RIDER", 2L), paid));
        assertFalse(OrderAccess.canPayOrCancel(new AuthUser(2, "RIDER", 2L), paid));
        paid.setRiderId(2L);
        paid.setStatus("ACCEPTED");
        assertFalse(OrderAccess.canView(new AuthUser(8, "RIDER", 8L), paid));
    }

    private static DeliveryOrder order(long userId, long merchantId, Long riderId, String status) {
        DeliveryOrder order = new DeliveryOrder();
        order.setId(100L);
        order.setUserId(userId);
        order.setMerchantId(merchantId);
        order.setRiderId(riderId);
        order.setStatus(status);
        return order;
    }
}

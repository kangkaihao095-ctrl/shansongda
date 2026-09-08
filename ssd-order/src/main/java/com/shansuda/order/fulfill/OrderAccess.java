package com.shansuda.order.fulfill;

import com.shansuda.common.auth.AuthUser;
import com.shansuda.order.domain.DeliveryOrder;

public final class OrderAccess {

    private OrderAccess() {
    }

    public static boolean canView(AuthUser auth, DeliveryOrder order) {
        if (auth == null || order == null) {
            return false;
        }
        if (order.getUserId() == auth.userId()) {
            return true;
        }
        if (auth.isMerchant() && order.getMerchantId() == auth.userId()) {
            return true;
        }
        if (auth.isRider() && order.getRiderId() != null && order.getRiderId() == auth.userId()) {
            return true;
        }
        return auth.isRider() && "PAID".equals(order.getStatus()) && order.getRiderId() == null;
    }

    public static boolean canPayOrCancel(AuthUser auth, DeliveryOrder order) {
        return auth != null && order != null && order.getUserId() == auth.userId();
    }
}

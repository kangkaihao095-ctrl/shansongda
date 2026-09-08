package com.shansuda.order.strategy;

import com.shansuda.common.api.BizException;
import com.shansuda.common.api.ErrorCodes;

public final class PenaltyStrategy {

    private PenaltyStrategy() {
    }

    public static int penaltyCents(String status, int freightCents) {
        return switch (status) {
            case "CREATED", "CANCELLING" -> 0;
            case "MERCHANT_PENDING", "PAID" -> 200;
            case "ACCEPTED", "ARRIVED" -> freightCents;
            case "DELIVERING", "COMPLETED", "CANCELLED", "REFUNDED", "REFUNDING", "REFUND_REJECTED" ->
                    throw BizException.conflict(ErrorCodes.ILLEGAL_STATE, "当前状态不可取消: " + status);
            default -> throw BizException.conflict(ErrorCodes.ILLEGAL_STATE, "未知状态: " + status);
        };
    }

    public static int refundPenaltyCents(String status, int freightCents) {
        return switch (status) {
            case "CREATED", "CANCELLING", "COMPLETED", "REFUND_REJECTED" -> 0;
            case "MERCHANT_PENDING", "PAID" -> 200;
            case "ACCEPTED", "ARRIVED", "DELIVERING" -> freightCents;
            default -> throw BizException.conflict(ErrorCodes.ILLEGAL_STATE, "当前状态不可退款: " + status);
        };
    }
}

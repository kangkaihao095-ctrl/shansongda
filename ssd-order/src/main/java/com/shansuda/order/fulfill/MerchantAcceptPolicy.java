package com.shansuda.order.fulfill;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * 商家出餐超时：超时后营业中自动接单，打烊则退款。时钟由调用方注入。
 */
public final class MerchantAcceptPolicy {

    public static final int DEFAULT_TIMEOUT_MIN = 15;

    public enum Action {
        WAIT, AUTO_ACCEPT, REFUND
    }

    private MerchantAcceptPolicy() {
    }

    public static Duration timeout(int minutes) {
        int m = minutes <= 0 ? DEFAULT_TIMEOUT_MIN : minutes;
        return Duration.ofMinutes(m);
    }

    public static boolean timedOut(Instant paidOrUpdatedAt, Instant now, Duration timeout) {
        if (paidOrUpdatedAt == null || now == null || timeout == null) {
            return false;
        }
        return !paidOrUpdatedAt.plus(timeout).isAfter(now);
    }

    public static Action action(boolean timedOut, boolean shopOpen) {
        if (!timedOut) {
            return Action.WAIT;
        }
        return shopOpen ? Action.AUTO_ACCEPT : Action.REFUND;
    }

    /** 营业中才可接新单 / 自动接单。open=false 或 onlineStatus=OFFLINE 视为打烊。 */
    public static boolean isOpen(Map<String, Object> merchant) {
        if (merchant == null) {
            return true;
        }
        if (Boolean.FALSE.equals(merchant.get("open"))) {
            return false;
        }
        Object status = merchant.get("onlineStatus");
        return status == null || !"OFFLINE".equals(String.valueOf(status));
    }

    public static boolean autoAcceptEnabled(Map<String, Object> merchant) {
        return merchant != null && Boolean.TRUE.equals(merchant.get("autoAccept")) && isOpen(merchant);
    }
}

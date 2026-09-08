package com.shansuda.account.catalog;

import com.shansuda.account.domain.Coupon;
import com.shansuda.common.api.BizException;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

public final class CouponPolicy {

    private CouponPolicy() {
    }

    public static int discountCents(Coupon coupon, int goodsCents) {
        Quote q = quote(coupon, goodsCents, null, Instant.now());
        if (!q.available()) {
            throw BizException.badRequest(q.reasonCode(), q.reason());
        }
        return q.discountCents();
    }

    public static Quote quote(Coupon coupon, int goodsCents, Long merchantId, Instant now) {
        if (coupon == null) {
            return Quote.no("COUPON_MISSING", "券不存在");
        }
        if (coupon.getMerchantId() != null && merchantId != null && !coupon.getMerchantId().equals(merchantId)) {
            return Quote.no("COUPON_SHOP", "该券仅限指定店铺");
        }
        Instant ts = now == null ? Instant.now() : now;
        if (coupon.getStartAt() != null && ts.isBefore(coupon.getStartAt())) {
            return Quote.no("COUPON_TIME", "券尚未开始");
        }
        if (coupon.getEndAt() != null && ts.isAfter(coupon.getEndAt())) {
            return Quote.no("COUPON_TIME", "券已过期");
        }
        int min = nz(coupon.getMinSpendCents());
        if (goodsCents < min) {
            return Quote.no("COUPON_MIN", "未满门槛");
        }
        int discount;
        if ("PERCENT".equals(coupon.getType())) {
            int percent = Math.min(100, Math.max(0, nz(coupon.getPercentOff())));
            discount = goodsCents * percent / 100;
        } else {
            discount = Math.min(nz(coupon.getDiscountCents()), Math.max(0, goodsCents));
        }
        return new Quote(true, "OK", "可用", discount);
    }

    /**
     * 可用券里应付最低；相同则到期更近；再相同选 id 更小。
     */
    public static Long bestCouponId(List<Candidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        return candidates.stream()
                .filter(Candidate::available)
                .min(Comparator.comparingInt(Candidate::payCents)
                        .thenComparing(c -> c.endAt() == null ? Instant.MAX : c.endAt())
                        .thenComparingLong(Candidate::couponId))
                .map(Candidate::couponId)
                .orElse(null);
    }

    private static int nz(Integer value) {
        return value == null ? 0 : value;
    }

    public record Quote(boolean available, String reasonCode, String reason, int discountCents) {
        public static Quote no(String code, String reason) {
            return new Quote(false, code, reason, 0);
        }
    }

    public record Candidate(long couponId, boolean available, int payCents, Instant endAt) {
    }
}

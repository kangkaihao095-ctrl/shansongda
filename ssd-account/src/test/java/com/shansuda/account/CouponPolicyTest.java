package com.shansuda.account;

import com.shansuda.account.catalog.CouponPolicy;
import com.shansuda.account.domain.Coupon;
import com.shansuda.common.api.BizException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CouponPolicyTest {

    @Test
    void amountCouponCapsAtGoods() {
        Coupon coupon = new Coupon();
        coupon.setType("AMOUNT");
        coupon.setMinSpendCents(2000);
        coupon.setDiscountCents(5000);
        assertEquals(2500, CouponPolicy.discountCents(coupon, 2500));
        coupon.setDiscountCents(500);
        assertEquals(500, CouponPolicy.discountCents(coupon, 2500));
        assertThrows(BizException.class, () -> CouponPolicy.discountCents(coupon, 1999));
    }

    @Test
    void percentCouponUsesPercentOff() {
        Coupon coupon = new Coupon();
        coupon.setType("PERCENT");
        coupon.setMinSpendCents(1500);
        coupon.setPercentOff(10);
        assertEquals(200, CouponPolicy.discountCents(coupon, 2000));
    }

    @Test
    void bestCouponPicksLowestPayThenSoonerExpiry() {
        Instant soon = Instant.parse("2026-01-01T00:00:00Z");
        Instant late = Instant.parse("2027-01-01T00:00:00Z");
        Long id = CouponPolicy.bestCouponId(List.of(
                new CouponPolicy.Candidate(10L, true, 800, late),
                new CouponPolicy.Candidate(11L, true, 500, late),
                new CouponPolicy.Candidate(12L, false, 100, soon)
        ));
        assertEquals(11L, id);
        Long tie = CouponPolicy.bestCouponId(List.of(
                new CouponPolicy.Candidate(10L, true, 500, late),
                new CouponPolicy.Candidate(11L, true, 500, soon)
        ));
        assertEquals(11L, tie);
    }

    @Test
    void expiredCouponCannotBeUsed() {
        Coupon coupon = new Coupon();
        coupon.setType("AMOUNT");
        coupon.setMinSpendCents(0);
        coupon.setDiscountCents(1500);
        coupon.setEndAt(Instant.parse("2020-01-01T00:00:00Z"));
        CouponPolicy.Quote q = CouponPolicy.quote(coupon, 2000, null, Instant.parse("2026-01-01T00:00:00Z"));
        assertEquals(false, q.available());
        assertEquals("COUPON_TIME", q.reasonCode());
    }
}

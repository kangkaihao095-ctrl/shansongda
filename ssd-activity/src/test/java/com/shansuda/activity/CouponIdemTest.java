package com.shansuda.activity;

import com.shansuda.activity.service.ActivityKeys;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CouponIdemTest {

    @Test
    void couponIdemKeyIsStablePerActivityAndUser() {
        String first = ActivityKeys.coupon(2L, 1L);
        String again = ActivityKeys.coupon(2L, 1L);
        assertEquals(first, again);
        Set<String> seen = new HashSet<>();
        assertTrue(seen.add(first));
        assertTrue(!seen.add(again));
    }
}

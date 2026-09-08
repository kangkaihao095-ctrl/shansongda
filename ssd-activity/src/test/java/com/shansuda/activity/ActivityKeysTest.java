package com.shansuda.activity;

import com.shansuda.activity.service.ActivityKeys;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ActivityKeysTest {

    @Test
    void seckillKeyIncludesSkuSoOldSlotDoesNotReplay() {
        String current = ActivityKeys.seckill(1, 9, 1001);
        String previous = ActivityKeys.seckill(1, 9, 1003);
        assertEquals("1:9:SECKILL:1001", current);
        assertNotEquals(current, previous);
        assertEquals("1:9:COUPON", ActivityKeys.coupon(1, 9));
    }
}

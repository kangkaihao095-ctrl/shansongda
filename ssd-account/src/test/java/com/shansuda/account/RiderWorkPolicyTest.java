package com.shansuda.account;

import com.shansuda.account.work.RiderWorkPolicy;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RiderWorkPolicyTest {

    @Test
    void accumulateCapsAtMaxAndForcesOffline() {
        int max = RiderWorkPolicy.maxSeconds(8);
        assertEquals(8 * 3600, max);
        assertEquals(max, RiderWorkPolicy.accumulate(8 * 3600 - 10, 30, max));
        assertTrue(RiderWorkPolicy.shouldForceOffline(max, max));
        assertFalse(RiderWorkPolicy.shouldForceOffline(max - 1, max));
        assertEquals(10, RiderWorkPolicy.remainingSeconds(max - 10, max));
    }

    @Test
    void cannotGoOnlineSameDayAfterForced() {
        LocalDate today = LocalDate.of(2026, 9, 4);
        assertFalse(RiderWorkPolicy.canGoOnline(true, today, today));
        assertTrue(RiderWorkPolicy.canGoOnline(true, today, today.plusDays(1)));
        assertTrue(RiderWorkPolicy.canGoOnline(false, today, today));
        assertFalse(RiderWorkPolicy.canAccept(true, 100, 28800));
        assertFalse(RiderWorkPolicy.canAccept(false, 28800, 28800));
        assertTrue(RiderWorkPolicy.canAccept(false, 100, 28800));
        assertFalse(RiderWorkPolicy.canGrab("OFFLINE", false, 100, 28800));
        assertFalse(RiderWorkPolicy.canGrab("ONLINE", true, 100, 28800));
        assertTrue(RiderWorkPolicy.canGrab("ONLINE", false, 100, 28800));
        assertEquals("NEED_ONLINE", RiderWorkPolicy.grabDenyCode("OFFLINE", false, 100, 28800));
        assertEquals("请先上线", RiderWorkPolicy.grabDenyReason("NEED_ONLINE"));
        assertEquals("WORK_LIMIT", RiderWorkPolicy.grabDenyCode("ONLINE", true, 100, 28800));
        assertEquals("WORK_LIMIT", RiderWorkPolicy.grabDenyCode("OFFLINE", false, 28800, 28800));
    }

    @Test
    void subsidyIsDailyPlusPerComplete() {
        assertEquals(1500 + 400, RiderWorkPolicy.subsidyCents(1500, 200, 2));
        assertEquals(1500, RiderWorkPolicy.subsidyCents(1500, 200, 0));
    }
}

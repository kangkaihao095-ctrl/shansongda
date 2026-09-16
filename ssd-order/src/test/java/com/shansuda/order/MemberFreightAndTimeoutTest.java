package com.shansuda.order;

import com.shansuda.order.fulfill.MerchantAcceptPolicy;
import com.shansuda.order.strategy.DispatchRank;
import com.shansuda.order.strategy.DistanceFreight;
import com.shansuda.order.strategy.FreightSelector;
import com.shansuda.order.strategy.FreightStrategy;
import com.shansuda.order.strategy.MemberFreight;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemberFreightAndTimeoutTest {

    @Test
    void memberFreightLv3AndYearCard() {
        DistanceFreight base = new DistanceFreight();
        Instant now = Instant.parse("2026-03-01T02:00:00Z");
        int raw = base.quote(31.238, 121.484, 31.224, 121.469, now);
        FreightStrategy lv3 = MemberFreight.wrap(base, new MemberFreight.Context(3, false, true));
        FreightStrategy year = MemberFreight.wrap(base, new MemberFreight.Context(3, true, true));
        FreightStrategy inactive = MemberFreight.wrap(base, new MemberFreight.Context(7, true, false));
        assertEquals(Math.max(0, raw - 200), lv3.quote(31.238, 121.484, 31.224, 121.469, now));
        assertEquals(Math.max(0, raw - 300), year.quote(31.238, 121.484, 31.224, 121.469, now));
        assertEquals("DistanceFreight+MemberFreight", lv3.name());
        assertEquals(raw, inactive.quote(31.238, 121.484, 31.224, 121.469, now));
        FreightSelector selector = new FreightSelector();
        FreightStrategy selected = selector.select(now, new MemberFreight.Context(3, true, true));
        assertTrue(selected.name().contains("MemberFreight"));
        assertEquals(0, Math.max(0, 200 - 300));
    }

    @Test
    void timeoutUsesInjectedClock() {
        Clock clock = Clock.fixed(Instant.parse("2026-09-11T04:20:00Z"), ZoneOffset.UTC);
        Instant paid = Instant.parse("2026-09-11T04:00:00Z");
        Duration timeout = MerchantAcceptPolicy.timeout(15);
        assertTrue(MerchantAcceptPolicy.timedOut(paid, clock.instant(), timeout));
        assertEquals(MerchantAcceptPolicy.Action.AUTO_ACCEPT,
                MerchantAcceptPolicy.action(true, true));
        assertEquals(MerchantAcceptPolicy.Action.REFUND,
                MerchantAcceptPolicy.action(true, false));
        assertEquals(MerchantAcceptPolicy.Action.WAIT,
                MerchantAcceptPolicy.action(false, true));
        Instant recent = Instant.parse("2026-09-11T04:10:00Z");
        assertFalse(MerchantAcceptPolicy.timedOut(recent, clock.instant(), timeout));
    }

    @Test
    void shopOpenAndAutoAcceptRespectOffline() {
        assertTrue(MerchantAcceptPolicy.isOpen(null));
        assertTrue(MerchantAcceptPolicy.isOpen(java.util.Map.of("onlineStatus", "ONLINE")));
        assertFalse(MerchantAcceptPolicy.isOpen(java.util.Map.of("onlineStatus", "OFFLINE")));
        assertFalse(MerchantAcceptPolicy.isOpen(java.util.Map.of("open", false, "onlineStatus", "ONLINE")));
        assertFalse(MerchantAcceptPolicy.autoAcceptEnabled(java.util.Map.of("autoAccept", true, "onlineStatus", "OFFLINE")));
        assertTrue(MerchantAcceptPolicy.autoAcceptEnabled(java.util.Map.of("autoAccept", true, "onlineStatus", "ONLINE")));
        assertFalse(MerchantAcceptPolicy.autoAcceptEnabled(java.util.Map.of("autoAccept", false, "onlineStatus", "ONLINE")));
    }

    @Test
    void dispatchRankUsesCreditWhenCostClose() {
        int cmp = DispatchRank.compare(10.0, 0.99, 4.9, 10.1, 0.80, 4.0);
        assertTrue(cmp < 0);
        int far = DispatchRank.compare(5.0, 0.5, 3.0, 12.0, 0.99, 5.0);
        assertTrue(far < 0);
        assertTrue(DispatchRank.close(10.0, 10.2));
        assertFalse(DispatchRank.close(10.0, 20.0));
        assertEquals(0.9 * 0.6 + (4.6 / 5.0) * 0.4, DispatchRank.credit(null, null), 1e-9);
        int nullCredit = DispatchRank.compare(10.0, null, null, 10.05, 0.99, 4.9);
        assertTrue(nullCredit > 0);
    }
}

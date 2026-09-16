package com.shansuda.account;

import com.shansuda.account.lbs.RiderTraceStore;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RiderTraceStoreTest {

    @Test
    void recentReturnsRecordedPoints() {
        RiderTraceStore store = new RiderTraceStore();
        store.record(2L, 31.2304, 121.4737);
        store.record(2L, 31.2310, 121.4740);
        List<Map<String, Object>> rows = store.recent(60);
        assertEquals(2, rows.size());
        assertEquals(2L, rows.get(0).get("userId"));
        assertEquals(31.2304, ((Number) rows.get(0).get("lat")).doubleValue(), 1e-6);
        assertTrue(rows.get(0).get("updateTime") != null);
    }

    @Test
    void keepsOnlyLatestWindowOfPoints() {
        RiderTraceStore store = new RiderTraceStore();
        for (int i = 0; i < 60; i++) {
            store.record(8L, 31.23 + i * 1e-5, 121.47);
        }
        assertEquals(48, store.recent(120).size());
    }
}

package com.shansuda.search;

import com.shansuda.search.es.EsRiderStore;
import com.shansuda.search.service.RiderDoc;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DEV_TASK §10：ES 旧 version 不覆盖。本机 9210 不可用则跳过（不把延迟数字写成 SLA）。
 */
class EsRiderStoreTest {

    @Test
    void olderVersionDoesNotOverwrite() {
        EsRiderStore store = new EsRiderStore("http://127.0.0.1:9210");
        Assumptions.assumeTrue(store.ping(), "本机 Elasticsearch 9210 不可用，跳过 EsRiderStore 集成测");
        store.ensureIndex();
        long riderId = 9_000_000L + Math.abs(UUID.randomUUID().getLeastSignificantBits() % 100_000);
        RiderDoc v2 = new RiderDoc(riderId, 31.23, 121.47, "ONLINE", "IDLE", Instant.now(), 2);
        RiderDoc v1 = new RiderDoc(riderId, 31.00, 121.00, "OFFLINE", "BUSY", Instant.now(), 1);
        assertTrue(store.upsert(v2));
        assertFalse(store.upsert(v1));
        var nearby = store.nearby(31.23, 121.47, 2000, "ONLINE", "IDLE");
        assertTrue(nearby.stream().anyMatch(d -> d.riderId() == riderId && d.version() == 2));
        assertEquals(2, nearby.stream().filter(d -> d.riderId() == riderId).findFirst().orElseThrow().version());
    }
}

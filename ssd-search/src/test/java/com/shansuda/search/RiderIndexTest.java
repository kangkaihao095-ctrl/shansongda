package com.shansuda.search;

import com.shansuda.search.service.RiderDoc;
import com.shansuda.search.service.RiderIndex;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RiderIndexTest {

    @Test
    void discardOlderVersion() {
        RiderIndex index = new RiderIndex();
        RiderDoc v2 = new RiderDoc(2, 31.23, 121.47, "ONLINE", "IDLE", Instant.now(), 2);
        RiderDoc v1 = new RiderDoc(2, 31.00, 121.00, "OFFLINE", "BUSY", Instant.now(), 1);
        assertTrue(index.upsert(v2));
        assertFalse(index.upsert(v1));
        assertEquals(2, index.get(2).version());
        assertEquals(31.23, index.get(2).lat());
    }
}

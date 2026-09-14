package com.shansuda.order;

import com.shansuda.order.query.SkuSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkuSnapshotReorderTest {

    @Test
    void completedSnapshotItemsAreReusable() {
        Map<String, Object> snap = Map.of(
                "shopName", "闪送达鲜生",
                "items", List.of(Map.of("skuId", 1001, "name", "杨枝甘露", "qty", 2, "priceCents", 1990))
        );
        assertTrue(SkuSnapshot.hasItems(snap));
        assertEquals(1001L, SkuSnapshot.items(snap).get(0).get("skuId"));
        assertEquals(2, SkuSnapshot.items(snap).get(0).get("qty"));
        assertFalse(SkuSnapshot.hasItems(Map.of("items", List.of())));
        assertFalse(SkuSnapshot.hasItems(Map.of()));
    }
}

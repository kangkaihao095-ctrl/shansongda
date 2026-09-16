package com.shansuda.order;

import com.shansuda.common.route.GridPathFinder;
import com.shansuda.order.route.RoutePathCache;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class RoutePathCacheTest {

    @Test
    void hitWithinTtlMissAfterExpiry() throws Exception {
        RoutePathCache cache = new RoutePathCache(Duration.ofMillis(80));
        GridPathFinder.Path path = new GridPathFinder.Path(List.of(1, 2), 1.2, "ASTAR");
        cache.putLeg(2L, 99L, RoutePathCache.R2M, path);
        assertEquals(0, cache.hitCount());
        GridPathFinder.Path hit = cache.getLeg(2L, 99L, RoutePathCache.R2M);
        assertNotNull(hit);
        assertEquals(1.2, hit.cost);
        assertEquals(1, cache.hitCount());
        Thread.sleep(120);
        assertNull(cache.getLeg(2L, 99L, RoutePathCache.R2M));
        assertEquals(1, cache.missCount());
    }

    @Test
    void r2uAndTwoLegDoNotCollideWithR2m() {
        RoutePathCache cache = new RoutePathCache();
        GridPathFinder.Path toShop = new GridPathFinder.Path(List.of(1, 2), 0.8, "ASTAR");
        GridPathFinder.Path toUser = new GridPathFinder.Path(List.of(2, 9), 1.1, "ASTAR");
        cache.putLeg(2L, 99L, RoutePathCache.R2M, toShop);
        cache.putLeg(2L, 99L, RoutePathCache.R2U, toUser);
        cache.putTwoLeg(2L, 99L, java.util.Map.of("etaMs", 120000L, "algorithm", "ASTAR"));
        assertEquals(0.8, cache.getLeg(2L, 99L, RoutePathCache.R2M).cost);
        assertEquals(1.1, cache.getLeg(2L, 99L, RoutePathCache.R2U).cost);
        assertEquals(120000L, ((Number) cache.getTwoLeg(2L, 99L).get("etaMs")).longValue());
        assertNull(cache.getLeg(2L, 100L, RoutePathCache.R2U));
    }
}

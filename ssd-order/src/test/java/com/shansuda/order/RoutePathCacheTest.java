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
}

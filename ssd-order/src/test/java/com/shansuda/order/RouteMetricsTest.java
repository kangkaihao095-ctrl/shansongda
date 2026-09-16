package com.shansuda.order;

import com.shansuda.order.route.RouteMetrics;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class RouteMetricsTest {

    @Test
    void countsEngineAlgorithmAndCacheWithoutSlaCopy() {
        RouteMetrics metrics = new RouteMetrics();
        metrics.recordEngine(false);
        metrics.recordEngine(true);
        metrics.recordAlgorithm("ASTAR");
        metrics.recordAlgorithm("DIJKSTRA");
        metrics.recordAlgorithm("HAVERSINE_FALLBACK");
        metrics.recordCache(true);
        metrics.recordCache(false);
        Map<String, Object> snap = metrics.snapshot(null);
        @SuppressWarnings("unchecked")
        Map<String, Object> engine = (Map<String, Object>) snap.get("engine");
        @SuppressWarnings("unchecked")
        Map<String, Object> algorithm = (Map<String, Object>) snap.get("algorithm");
        assertEquals(1L, engine.get("MEMORY_OSM"));
        assertEquals(1L, engine.get("NEO4J_GDS"));
        assertEquals(1L, algorithm.get("ASTAR"));
        assertEquals(1L, algorithm.get("DIJKSTRA"));
        assertEquals(1L, algorithm.get("HAVERSINE_FALLBACK"));
        assertEquals(1L, snap.get("fallback"));
        assertEquals(1L, snap.get("cacheHits"));
        assertEquals(1L, snap.get("cacheMisses"));
        assertFalse(snap.toString().contains("P95"));
        assertFalse(snap.toString().contains("QPS"));
    }
}

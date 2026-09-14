package com.shansuda.order.route;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/** 算路 engine / algorithm / fallback 计数，供演示排障，不是 SLA。 */
public final class RouteMetrics {

    private final AtomicLong neo4jGds = new AtomicLong();
    private final AtomicLong memoryOsm = new AtomicLong();
    private final AtomicLong astar = new AtomicLong();
    private final AtomicLong dijkstra = new AtomicLong();
    private final AtomicLong haversineFallback = new AtomicLong();
    private final AtomicLong cacheHits = new AtomicLong();
    private final AtomicLong cacheMisses = new AtomicLong();

    public void recordEngine(boolean neo4j) {
        if (neo4j) {
            neo4jGds.incrementAndGet();
        } else {
            memoryOsm.incrementAndGet();
        }
    }

    public void recordAlgorithm(String algorithm) {
        if (RouteService.HAVERSINE_FALLBACK.equals(algorithm)) {
            haversineFallback.incrementAndGet();
            return;
        }
        if ("DIJKSTRA".equals(algorithm)) {
            dijkstra.incrementAndGet();
            return;
        }
        astar.incrementAndGet();
    }

    public void recordCache(boolean hit) {
        if (hit) {
            cacheHits.incrementAndGet();
        } else {
            cacheMisses.incrementAndGet();
        }
    }

    public Map<String, Object> snapshot(RoutePathCache cache) {
        Map<String, Object> engine = new LinkedHashMap<>();
        engine.put("NEO4J_GDS", neo4jGds.get());
        engine.put("MEMORY_OSM", memoryOsm.get());
        Map<String, Object> algorithm = new LinkedHashMap<>();
        algorithm.put("ASTAR", astar.get());
        algorithm.put("DIJKSTRA", dijkstra.get());
        algorithm.put("HAVERSINE_FALLBACK", haversineFallback.get());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("engine", engine);
        body.put("algorithm", algorithm);
        body.put("fallback", haversineFallback.get());
        body.put("cacheHits", cache == null ? cacheHits.get() : cache.hitCount());
        body.put("cacheMisses", cache == null ? cacheMisses.get() : cache.missCount());
        body.put("cacheSize", cache == null ? 0 : cache.size());
        return body;
    }
}

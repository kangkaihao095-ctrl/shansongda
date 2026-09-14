package com.shansuda.order.route;

import com.shansuda.common.route.GridPathFinder;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 大厅 / 指派共享的 (riderId, orderId, kind) 路径缓存，默认 45 秒。
 */
public final class RoutePathCache {

    public static final Duration TTL = Duration.ofSeconds(45);

    public static final String R2M = "R2M";
    public static final String R2U = "R2U";
    public static final String TWO = "TWO";

    private final Duration ttl;
    private final Map<String, Entry> store = new ConcurrentHashMap<>();
    private final AtomicLong hits = new AtomicLong();
    private final AtomicLong misses = new AtomicLong();

    public RoutePathCache() {
        this(TTL);
    }

    public RoutePathCache(Duration ttl) {
        this.ttl = ttl == null ? TTL : ttl;
    }

    public GridPathFinder.Path getLeg(long riderId, long orderId, String kind) {
        Object raw = get(key(riderId, orderId, kind));
        return raw instanceof GridPathFinder.Path path ? path : null;
    }

    public void putLeg(long riderId, long orderId, String kind, GridPathFinder.Path path) {
        if (path == null) {
            return;
        }
        store.put(key(riderId, orderId, kind), new Entry(path, Instant.now()));
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getTwoLeg(long riderId, long orderId) {
        Object raw = get(key(riderId, orderId, TWO));
        return raw instanceof Map<?, ?> map ? (Map<String, Object>) map : null;
    }

    public void putTwoLeg(long riderId, long orderId, Map<String, Object> route) {
        if (route == null) {
            return;
        }
        store.put(key(riderId, orderId, TWO), new Entry(route, Instant.now()));
    }

    public long hitCount() {
        return hits.get();
    }

    public long missCount() {
        return misses.get();
    }

    public int size() {
        return store.size();
    }

    public void clear() {
        store.clear();
        hits.set(0);
        misses.set(0);
    }

    private Object get(String key) {
        Entry entry = store.get(key);
        if (entry == null) {
            misses.incrementAndGet();
            return null;
        }
        if (entry.at.plus(ttl).isBefore(Instant.now())) {
            store.remove(key, entry);
            misses.incrementAndGet();
            return null;
        }
        hits.incrementAndGet();
        return entry.value;
    }

    static String key(long riderId, long orderId, String kind) {
        return riderId + ":" + orderId + ":" + (kind == null ? R2M : kind);
    }

    private record Entry(Object value, Instant at) {
    }
}

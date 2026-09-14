package com.shansuda.account.lbs;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * 骑手位置自动上报的短时轨迹窗口，供路网拥堵聚合。只记坐标，不写 Neo4j/MySQL 业务字段。
 */
@Component
public class RiderTraceStore {

    private static final int MAX_POINTS = 48;

    public record Point(double lat, double lon, Instant at) {
    }

    private final ConcurrentHashMap<Long, ConcurrentLinkedDeque<Point>> traces = new ConcurrentHashMap<>();

    public void record(long userId, double lat, double lon) {
        ConcurrentLinkedDeque<Point> q = traces.computeIfAbsent(userId, k -> new ConcurrentLinkedDeque<>());
        q.addLast(new Point(lat, lon, Instant.now()));
        while (q.size() > MAX_POINTS) {
            q.pollFirst();
        }
    }

    public List<Map<String, Object>> recent(int withinSeconds) {
        Instant cutoff = Instant.now().minusSeconds(Math.max(30, withinSeconds));
        List<Map<String, Object>> rows = new ArrayList<>();
        traces.forEach((userId, q) -> {
            for (Point p : q) {
                if (p.at().isBefore(cutoff)) {
                    continue;
                }
                rows.add(Map.of(
                        "userId", userId,
                        "lat", p.lat(),
                        "lon", p.lon(),
                        "updateTime", p.at()
                ));
            }
        });
        return rows;
    }
}

package com.shansuda.common.route;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 把骑手最近位置映射到邻近路段（默认 80m 内），按命中次数更新 congestion / cost。
 * 生产分钟级边权：有 Key 走高德态势，否则 {@link TrafficProfile} 时段画像；本类种子点仅作高峰补充。
 */
public final class CongestionAggregator {

    /** 位置落到路段的命中半径，让演示路径同时出现畅通 / 缓行 / 拥堵。 */
    static final double HIT_METERS = 100;

    private CongestionAggregator() {
    }

    /** 南京东路 / 人民广场 / 外滩一带演示轨迹点，对齐种子商家 31.238,121.484。 */
    public static List<double[]> seedTraces() {
        List<double[]> pts = new ArrayList<>();
        pts.add(new double[]{31.2380, 121.4840});
        pts.add(new double[]{31.2384, 121.4875});
        pts.add(new double[]{31.2386, 121.4902});
        pts.add(new double[]{31.2304, 121.4737});
        pts.add(new double[]{31.2320, 121.4780});
        pts.add(new double[]{31.2345, 121.4820});
        pts.add(new double[]{31.2240, 121.4690});
        pts.add(new double[]{31.2265, 121.4737});
        pts.add(new double[]{31.2355, 121.4900});
        interpolate(pts, 31.2304, 121.4737, 31.2380, 121.4840, 5);
        interpolate(pts, 31.2380, 121.4840, 31.2240, 121.4690, 6);
        return pts;
    }

    /** 无骑手点时的轻度种子，避免全图空白；比 seedTraces 更稀。 */
    public static List<double[]> lightSeedTraces() {
        List<double[]> pts = new ArrayList<>();
        pts.add(new double[]{31.2380, 121.4840});
        pts.add(new double[]{31.2384, 121.4875});
        pts.add(new double[]{31.2304, 121.4737});
        pts.add(new double[]{31.2320, 121.4780});
        pts.add(new double[]{31.2240, 121.4690});
        interpolate(pts, 31.2304, 121.4737, 31.2380, 121.4840, 3);
        return pts;
    }

    public static String hint(List<Map<String, Object>> segments) {
        double max = 1.0;
        if (segments != null) {
            for (Map<String, Object> seg : segments) {
                if (seg.get("congestion") instanceof Number n) {
                    max = Math.max(max, n.doubleValue());
                }
            }
        }
        if (max >= 1.6) {
            return "拥堵";
        }
        if (max >= 1.3) {
            return "缓行";
        }
        return "畅通";
    }

    static void interpolate(List<double[]> pts, double lat1, double lon1, double lat2, double lon2, int steps) {
        for (int i = 1; i < steps; i++) {
            double t = i / (double) steps;
            pts.add(new double[]{lat1 + (lat2 - lat1) * t, lon1 + (lon2 - lon1) * t});
        }
    }

    public static List<Map<String, Object>> overlay(GridPathFinder graph, List<double[]> positions) {
        return apply(graph, positions, false);
    }

    public static List<Map<String, Object>> apply(GridPathFinder graph, List<double[]> positions) {
        return apply(graph, positions, true);
    }

    /**
     * @param decayNonHits true=无命中路段向 1.0 衰减（种子全图）；false=只改命中路段（骑手覆盖）。
     */
    public static List<Map<String, Object>> apply(GridPathFinder graph, List<double[]> positions, boolean decayNonHits) {
        Map<String, Integer> hits = new LinkedHashMap<>();
        Map<String, GridPathFinder.Edge> byRoad = new LinkedHashMap<>();
        graph.adj().forEach((fromId, edges) -> {
            for (GridPathFinder.Edge edge : edges) {
                byRoad.put(edge.roadId, edge);
            }
        });
        if (positions != null && !positions.isEmpty()) {
            for (double[] p : positions) {
                GridPathFinder.Edge nearest = graph.nearestEdge(p[0], p[1]);
                if (nearest != null) {
                    hits.merge(nearest.roadId, 1, Integer::sum);
                    String reverse = reverseRoadId(nearest.roadId);
                    if (reverse != null && byRoad.containsKey(reverse)) {
                        hits.merge(reverse, 1, Integer::sum);
                    }
                }
                graph.adj().forEach((fromId, edges) -> {
                    GridPathFinder.Node from = graph.node(fromId);
                    if (from == null || !from.hasCoord()) {
                        return;
                    }
                    for (GridPathFinder.Edge edge : edges) {
                        GridPathFinder.Node to = graph.node(edge.to);
                        if (to == null || !to.hasCoord()) {
                            continue;
                        }
                        double d = GridPathFinder.pointToSegmentMeters(
                                p[0], p[1], from.lat, from.lon, to.lat, to.lon);
                        if (d <= HIT_METERS) {
                            hits.merge(edge.roadId, 1, Integer::sum);
                        }
                    }
                });
            }
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        byRoad.forEach((roadId, edge) -> {
            int count = hits.getOrDefault(roadId, 0);
            double congestion;
            if (count > 0) {
                congestion = 1.0 + Math.min(1.6, count * 0.35);
            } else if (!decayNonHits) {
                return;
            } else {
                congestion = 1.0 + (edge.congestion - 1.0) * 0.65;
                if (Math.abs(congestion - 1.0) < 0.03) {
                    congestion = 1.0;
                }
            }
            if (Math.abs(congestion - edge.congestion) < 1e-6) {
                return;
            }
            edge.setCongestion(congestion);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("roadId", roadId);
            row.put("congestion", congestion);
            rows.add(row);
        });
        return rows;
    }

    /** 高峰补充：只抬最近路段，不衰减其余边，不盖过时段+等级主因。 */
    static void boostNear(GridPathFinder graph, List<double[]> positions, double extra,
                          List<Map<String, Object>> rows) {
        if (graph == null || positions == null || extra <= 0) {
            return;
        }
        Map<String, GridPathFinder.Edge> byRoad = new LinkedHashMap<>();
        graph.adj().forEach((fromId, edges) -> {
            for (GridPathFinder.Edge edge : edges) {
                byRoad.put(edge.roadId, edge);
            }
        });
        for (double[] p : positions) {
            GridPathFinder.Edge nearest = graph.nearestEdge(p[0], p[1]);
            if (nearest == null) {
                continue;
            }
            bump(nearest, extra, rows);
            String reverse = reverseRoadId(nearest.roadId);
            GridPathFinder.Edge other = reverse == null ? null : byRoad.get(reverse);
            if (other != null) {
                bump(other, extra, rows);
            }
        }
    }

    private static void bump(GridPathFinder.Edge edge, double extra, List<Map<String, Object>> rows) {
        double next = Math.min(2.4, edge.congestion + extra);
        if (Math.abs(next - edge.congestion) < 1e-6) {
            return;
        }
        edge.setCongestion(next);
        if (rows != null) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("roadId", edge.roadId);
            row.put("congestion", next);
            rows.add(row);
        }
    }

    static String reverseRoadId(String roadId) {
        if (roadId == null) {
            return null;
        }
        if (roadId.endsWith("a")) {
            return roadId.substring(0, roadId.length() - 1) + "b";
        }
        if (roadId.endsWith("b")) {
            return roadId.substring(0, roadId.length() - 1) + "a";
        }
        return null;
    }
}

package com.shansuda.dispatch.grid;

import com.shansuda.common.route.GridPathFinder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 把骑手最近位置映射到途经边，按命中次数更新 congestion / cost。
 * 无真实轨迹时由调用方传入黄浦种子坐标，算法仍是「位置→路段」，不用随机数。
 */
public final class CongestionAggregator {

    private CongestionAggregator() {
    }

    /** 南京东路 / 人民广场 / 外滩一带演示轨迹点，对齐种子商家 31.238,121.484。 */
    public static List<double[]> seedTraces() {
        return List.of(
                new double[]{31.2380, 121.4840},
                new double[]{31.2384, 121.4875},
                new double[]{31.2386, 121.4902},
                new double[]{31.2304, 121.4737},
                new double[]{31.2320, 121.4780},
                new double[]{31.2345, 121.4820},
                new double[]{31.2240, 121.4690},
                new double[]{31.2265, 121.4737},
                new double[]{31.2355, 121.4900}
        );
    }

    public static List<Map<String, Object>> apply(GridPathFinder graph, List<double[]> positions) {
        Map<String, Integer> hits = new LinkedHashMap<>();
        Map<String, GridPathFinder.Edge> byRoad = new LinkedHashMap<>();
        graph.adj().values().forEach(edges -> {
            for (GridPathFinder.Edge edge : edges) {
                byRoad.put(edge.roadId, edge);
            }
        });
        if (!positions.isEmpty()) {
            for (double[] p : positions) {
                GridPathFinder.Edge nearest = graph.nearestEdge(p[0], p[1]);
                if (nearest == null) {
                    continue;
                }
                hits.merge(nearest.roadId, 1, Integer::sum);
                String reverse = reverseRoadId(nearest.roadId);
                if (reverse != null && byRoad.containsKey(reverse)) {
                    hits.merge(reverse, 1, Integer::sum);
                }
            }
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        byRoad.forEach((roadId, edge) -> {
            int count = hits.getOrDefault(roadId, 0);
            double congestion;
            if (count > 0) {
                congestion = 1.0 + Math.min(1.6, count * 0.35);
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

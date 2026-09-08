package com.shansuda.order.route;

import com.shansuda.common.route.GridPathFinder;
import com.shansuda.common.route.OsmGraphLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 本机无 Neo4j 进程时用同构内存路网做 A-star / Dijkstra，不新起 ssd-dispatch。
 * 算法与 dispatch 模块一致，不是高德算路。
 */
@Service
public class RouteService {

    private static final Logger log = LoggerFactory.getLogger(RouteService.class);

    private final GridPathFinder graph;

    public RouteService() {
        this.graph = OsmGraphLoader.load();
        log.info("订单内嵌路网就绪：节点 {} 边 {}（内存 A*/Dijkstra）", graph.nodes().size(), graph.edgeCount());
    }

    public Map<String, Object> route(double riderLat, double riderLon, double merchantLat, double merchantLon,
                                     double userLat, double userLon) {
        GridPathFinder.Path first = segment(riderLat, riderLon, merchantLat, merchantLon);
        GridPathFinder.Path second = segment(merchantLat, merchantLon, userLat, userLon);
        Map<String, Object> riderToMerchant = leg(first);
        Map<String, Object> merchantToUser = leg(second);
        List<Map<String, Object>> points = new ArrayList<>();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> firstPts = (List<Map<String, Object>>) riderToMerchant.get("points");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> secondPts = (List<Map<String, Object>>) merchantToUser.get("points");
        points.addAll(firstPts);
        points.addAll(secondPts);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("riderToMerchant", riderToMerchant);
        body.put("merchantToUser", merchantToUser);
        body.put("points", points);
        body.put("cost", first.cost + second.cost);
        body.put("etaMs", Math.round((first.cost + second.cost) * 60_000));
        body.put("algorithm", first.algorithm);
        body.put("waypoints", Map.of(
                "rider", Map.of("lat", riderLat, "lon", riderLon),
                "merchant", Map.of("lat", merchantLat, "lon", merchantLon),
                "user", Map.of("lat", userLat, "lon", userLon)
        ));
        return body;
    }

    private GridPathFinder.Path segment(double fromLat, double fromLon, double toLat, double toLon) {
        GridPathFinder.Node src = graph.nearest(fromLat, fromLon);
        GridPathFinder.Node dst = graph.nearest(toLat, toLon);
        boolean astar = src.hasCoord() && dst.hasCoord();
        try {
            return graph.shortest(src.id, dst.id);
        } catch (IllegalStateException ex) {
            log.warn("路网暂不可达，退回最近路口连线: {}", ex.getMessage());
            double km = GridPathFinder.haversineKm(src.lat, src.lon, dst.lat, dst.lon);
            return new GridPathFinder.Path(List.of(src.id, dst.id), Math.max(0.2, km * 2.2), astar ? "ASTAR" : "DIJKSTRA");
        }
    }

    private Map<String, Object> leg(GridPathFinder.Path path) {
        List<Map<String, Object>> points = new ArrayList<>();
        for (int nodeId : path.nodeIds) {
            GridPathFinder.Node node = graph.node(nodeId);
            if (node != null && node.hasCoord()) {
                points.add(Map.of("lat", node.lat, "lon", node.lon));
            }
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("nodeIds", path.nodeIds);
        body.put("points", points);
        body.put("cost", path.cost);
        body.put("algorithm", path.algorithm);
        return body;
    }
}

package com.shansuda.order.route;

import com.shansuda.common.route.AmapTrafficClient;
import com.shansuda.common.route.CongestionAggregator;
import com.shansuda.common.route.GridPathFinder;
import com.shansuda.common.route.Neo4jRoadStore;
import com.shansuda.common.route.OsmGraphLoader;
import com.shansuda.common.route.TrafficProfile;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 本机四进程不启 ssd-dispatch。默认加载黄浦 OSM 内存图做 A-star / Dijkstra（不是高德算路）。
 * 拥堵：有 Key 走高德态势写边权，否则时段路况画像；GDS A* 仍读 Neo4j cost。
 * 启动 ping bolt：通则导入并走 GDS A-star（无坐标/启发式失败则 Dijkstra）；不通则 warn 降级内存 OSM A-star。
 * 路网不可达标 HAVERSINE_FALLBACK，不假标 ASTAR。
 */
@Service
public class RouteService {

    public static final String HAVERSINE_FALLBACK = "HAVERSINE_FALLBACK";
    public static final String TRAFFIC_AMAP = TrafficProfile.AMAP;
    public static final String TRAFFIC_LAST_SUCCESS = TrafficProfile.LAST_SUCCESS;
    public static final String TRAFFIC_PROFILE = TrafficProfile.PROFILE;
    public static final String TRAFFIC_DEFAULT = TrafficProfile.DEFAULT;

    private static final Logger log = LoggerFactory.getLogger(RouteService.class);

    private final GridPathFinder graph;
    private Neo4jRoadStore neo4j;
    private boolean useNeo4j;
    private final String amapKey;
    private final Clock clock;
    private final RoutePathCache pathCache = new RoutePathCache();
    private final RouteMetrics metrics = new RouteMetrics();
    private volatile String trafficSource = TRAFFIC_DEFAULT;
    private volatile String trafficPeriod = "";
    private volatile boolean amapEverSucceeded;

    @Autowired
    public RouteService(@Value("${ssd.mode:auto}") String mode,
                        @Value("${ssd.neo4j.uri:bolt://127.0.0.1:7687}") String neo4jUri,
                        @Value("${ssd.neo4j.username:neo4j}") String neo4jUser,
                        @Value("${ssd.neo4j.password:shansuda}") String neo4jPassword,
                        @Value("${ssd.amap.key:}") String amapKey) {
        this.graph = OsmGraphLoader.load();
        this.amapKey = amapKey == null ? "" : amapKey.trim();
        this.clock = Clock.system(TrafficProfile.ZONE);
        if (this.amapKey.isEmpty()) {
            applyTrafficSnapshot(AmapTrafficClient.TrafficSnapshot.fail("NO_KEY"));
        }
        connectNeo4j(mode, neo4jUri, neo4jUser, neo4jPassword);
        log.info("订单内嵌路网就绪：节点 {} 边 {}（{}）amapKey={} trafficSource={}",
                graph.nodes().size(), graph.edgeCount(),
                useNeo4j ? "Neo4j GDS A*" : "内存 OSM A*/Dijkstra",
                this.amapKey.isEmpty() ? "无" : "已配置",
                trafficSource);
    }

    /** 单测注入已有图，不连 Neo4j、不自动拉高德。 */
    public static RouteService withGraph(GridPathFinder graph) {
        return new RouteService(graph, Clock.system(TrafficProfile.ZONE));
    }

    public static RouteService withGraph(GridPathFinder graph, Clock clock) {
        return new RouteService(graph, clock);
    }

    private RouteService(GridPathFinder graph, Clock clock) {
        this.graph = graph;
        this.amapKey = "";
        this.clock = clock == null ? Clock.system(TrafficProfile.ZONE) : clock;
    }

    GridPathFinder graph() {
        return graph;
    }

    private void connectNeo4j(String mode, String uri, String user, String password) {
        if ("dry-run".equals(mode)) {
            return;
        }
        try {
            neo4j = new Neo4jRoadStore(uri, user, password);
            if (neo4j.ping()) {
                neo4j.importGrid(graph);
                useNeo4j = true;
                log.info("Neo4j 路网已导入，规划走 GDS A*（缺坐标/启发式失败降级 Dijkstra）");
            } else {
                neo4j.close();
                neo4j = null;
                log.warn("Neo4j ping 失败，降级内存 OSM A*");
            }
        } catch (Exception ex) {
            log.warn("Neo4j 初始化失败，降级内存 OSM A*（不拖垮订单启动）: {}", ex.getMessage());
            useNeo4j = false;
            closeQuietly();
        }
    }

    @PreDestroy
    public void shutdown() {
        closeQuietly();
    }

    private void closeQuietly() {
        if (neo4j != null) {
            try {
                neo4j.close();
            } catch (Exception ignored) {
                // ignore
            }
            neo4j = null;
        }
        useNeo4j = false;
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
        List<Map<String, Object>> segments = new ArrayList<>();
        segments.addAll(graph.segments(first));
        segments.addAll(graph.segments(second));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("riderToMerchant", riderToMerchant);
        body.put("merchantToUser", merchantToUser);
        body.put("legs", List.of(riderToMerchant, merchantToUser));
        body.put("segments", segments);
        body.put("points", points);
        body.put("cost", first.cost + second.cost);
        body.put("etaMs", Math.round((first.cost + second.cost) * 60_000));
        body.put("algorithm", combineAlgorithm(first.algorithm, second.algorithm));
        body.put("engine", useNeo4j ? "NEO4J_GDS" : "MEMORY_OSM");
        putTrafficMeta(body);
        metrics.recordEngine(useNeo4j);
        metrics.recordAlgorithm(String.valueOf(body.get("algorithm")));
        log.info("规划完成 engine={} algorithm={} points={} segments={}",
                useNeo4j ? "Neo4j GDS" : "内存 OSM",
                body.get("algorithm"), points.size(), segments.size());
        body.put("waypoints", Map.of(
                "rider", Map.of("lat", riderLat, "lon", riderLon),
                "merchant", Map.of("lat", merchantLat, "lon", merchantLon),
                "user", Map.of("lat", userLat, "lon", userLon)
        ));
        return body;
    }

    public GridPathFinder.Path riderToMerchant(double riderLat, double riderLon, double merchantLat, double merchantLon) {
        return segment(riderLat, riderLon, merchantLat, merchantLon);
    }

    /** 配送中 / 已到店：骑手当前坐标 → 用户，不要把商家当起点。 */
    public GridPathFinder.Path riderToUser(double riderLat, double riderLon, double userLat, double userLon) {
        return segment(riderLat, riderLon, userLat, userLon);
    }

    public GridPathFinder.Path haversinePath(double fromLat, double fromLon, double toLat, double toLon) {
        double km = GridPathFinder.haversineKm(fromLat, fromLon, toLat, toLon);
        GridPathFinder.Path path = new GridPathFinder.Path(List.of(), Math.max(0.2, km * 2.2), HAVERSINE_FALLBACK);
        metrics.recordAlgorithm(HAVERSINE_FALLBACK);
        return path;
    }

    public GridPathFinder.Path cachedLeg(long riderId, long orderId, String kind,
                                         double fromLat, double fromLon, double toLat, double toLon) {
        GridPathFinder.Path hit = pathCache.getLeg(riderId, orderId, kind);
        if (hit != null) {
            metrics.recordCache(true);
            return hit;
        }
        metrics.recordCache(false);
        GridPathFinder.Path path = segment(fromLat, fromLon, toLat, toLon);
        pathCache.putLeg(riderId, orderId, kind, path);
        return path;
    }

    public Map<String, Object> cachedTwoLeg(long riderId, long orderId,
                                            double riderLat, double riderLon,
                                            double merchantLat, double merchantLon,
                                            double userLat, double userLon) {
        Map<String, Object> hit = pathCache.getTwoLeg(riderId, orderId);
        if (hit != null) {
            metrics.recordCache(true);
            return hit;
        }
        GridPathFinder.Path first = pathCache.getLeg(riderId, orderId, RoutePathCache.R2M);
        if (first == null) {
            metrics.recordCache(false);
            first = cachedLeg(riderId, orderId, RoutePathCache.R2M, riderLat, riderLon, merchantLat, merchantLon);
        } else {
            metrics.recordCache(true);
        }
        GridPathFinder.Path second = segment(merchantLat, merchantLon, userLat, userLon);
        Map<String, Object> body = assemble(first, second, riderLat, riderLon, merchantLat, merchantLon, userLat, userLon);
        pathCache.putTwoLeg(riderId, orderId, body);
        return body;
    }

    public RoutePathCache pathCache() {
        return pathCache;
    }

    public Map<String, Object> metricsSnapshot() {
        Map<String, Object> body = metrics.snapshot(pathCache);
        body.put("trafficSource", trafficSource);
        body.put("amapKeyConfigured", !amapKey.isEmpty());
        return body;
    }

    public Map<String, Object> riderToUserRoute(double riderLat, double riderLon, double userLat, double userLon) {
        GridPathFinder.Path path = riderToUser(riderLat, riderLon, userLat, userLon);
        Map<String, Object> riderToUser = leg(path);
        List<Map<String, Object>> points = new ArrayList<>();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> pts = (List<Map<String, Object>>) riderToUser.get("points");
        points.addAll(pts);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("riderToUser", riderToUser);
        body.put("legs", List.of(riderToUser));
        body.put("segments", graph.segments(path));
        body.put("points", points);
        body.put("cost", path.cost);
        body.put("etaMs", Math.round(path.cost * 60_000));
        body.put("algorithm", path.algorithm);
        body.put("engine", useNeo4j ? "NEO4J_GDS" : "MEMORY_OSM");
        putTrafficMeta(body);
        body.put("waypoints", Map.of(
                "rider", Map.of("lat", riderLat, "lon", riderLon),
                "user", Map.of("lat", userLat, "lon", userLon)
        ));
        metrics.recordEngine(useNeo4j);
        metrics.recordAlgorithm(path.algorithm);
        return body;
    }

    @Scheduled(initialDelay = 4000, fixedDelayString = "${ssd.dispatch.congestion-ms:60000}")
    public void refreshCongestion() {
        applyTrafficSnapshot(AmapTrafficClient.fetch(graph, amapKey));
    }

    /** 单测可注入快照：高德失败有上次则保留，否则改走时段画像。 */
    public void applyTrafficSnapshot(AmapTrafficClient.TrafficSnapshot snapshot) {
        TrafficProfile.Decision decision = TrafficProfile.refresh(graph, snapshot, clock, amapEverSucceeded);
        trafficSource = decision.source();
        trafficPeriod = decision.periodLabel() == null ? "" : decision.periodLabel();
        if (TRAFFIC_AMAP.equals(decision.source())) {
            amapEverSucceeded = true;
        }
        if (!decision.wrote()) {
            String err = snapshot == null ? "NULL" : snapshot.error();
            log.warn("高德交通态势失败，保留上一轮边权 source={} err={}", trafficSource, err);
            return;
        }
        pathCache.clear();
        if (TRAFFIC_PROFILE.equals(decision.source())) {
            String err = snapshot == null ? "NULL" : snapshot.error();
            log.info("时段路况画像已写入边权：period={} 变更 {}（{}）cause={}",
                    trafficPeriod, decision.rows().size(),
                    useNeo4j ? "同步 Neo4j" : "内存图", err);
        } else {
            log.info("高德态势已写入边权：变更 {}（{}）",
                    decision.rows().size(), useNeo4j ? "同步 Neo4j" : "内存图");
        }
        if (useNeo4j && neo4j != null && !decision.rows().isEmpty()) {
            try {
                neo4j.updateCongestion(decision.rows());
            } catch (Exception ex) {
                log.warn("Neo4j 拥堵更新失败，本轮仅内存边权生效: {}", ex.getMessage());
            }
        }
    }

    public String trafficSource() {
        return trafficSource;
    }

    private GridPathFinder.Path segment(double fromLat, double fromLon, double toLat, double toLon) {
        GridPathFinder.Node src = graph.nearest(fromLat, fromLon);
        GridPathFinder.Node dst = graph.nearest(toLat, toLon);
        boolean astar = src.hasCoord() && dst.hasCoord();
        if (useNeo4j && neo4j != null) {
            try {
                GridPathFinder.Path gds = neo4j.shortest(src.id, dst.id, astar);
                log.debug("Neo4j GDS {} {} -> {}", gds.algorithm, src.id, dst.id);
                metrics.recordEngine(true);
                metrics.recordAlgorithm(gds.algorithm);
                return gds;
            } catch (Exception ex) {
                log.warn("GDS 规划失败，降级内存 OSM A*: {}", ex.getMessage());
            }
        }
        try {
            GridPathFinder.Path mem = graph.shortest(src.id, dst.id);
            metrics.recordEngine(false);
            metrics.recordAlgorithm(mem.algorithm);
            return mem;
        } catch (IllegalStateException ex) {
            log.warn("路网暂不可达，退回 Haversine 直线: {}", ex.getMessage());
            double km = GridPathFinder.haversineKm(src.lat, src.lon, dst.lat, dst.lon);
            metrics.recordAlgorithm(HAVERSINE_FALLBACK);
            return new GridPathFinder.Path(List.of(src.id, dst.id), Math.max(0.2, km * 2.2), HAVERSINE_FALLBACK);
        }
    }

    public String congestionHint(GridPathFinder.Path path) {
        return CongestionAggregator.hint(graph.segments(path));
    }

    public static String combineAlgorithm(String first, String second) {
        if (HAVERSINE_FALLBACK.equals(first) || HAVERSINE_FALLBACK.equals(second)) {
            return HAVERSINE_FALLBACK;
        }
        return first;
    }

    private Map<String, Object> assemble(GridPathFinder.Path first, GridPathFinder.Path second,
                                         double riderLat, double riderLon, double merchantLat, double merchantLon,
                                         double userLat, double userLon) {
        Map<String, Object> riderToMerchant = leg(first);
        Map<String, Object> merchantToUser = leg(second);
        List<Map<String, Object>> points = new ArrayList<>();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> firstPts = (List<Map<String, Object>>) riderToMerchant.get("points");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> secondPts = (List<Map<String, Object>>) merchantToUser.get("points");
        points.addAll(firstPts);
        points.addAll(secondPts);
        List<Map<String, Object>> segments = new ArrayList<>();
        segments.addAll(graph.segments(first));
        segments.addAll(graph.segments(second));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("riderToMerchant", riderToMerchant);
        body.put("merchantToUser", merchantToUser);
        body.put("legs", List.of(riderToMerchant, merchantToUser));
        body.put("segments", segments);
        body.put("points", points);
        body.put("cost", first.cost + second.cost);
        body.put("etaMs", Math.round((first.cost + second.cost) * 60_000));
        body.put("algorithm", combineAlgorithm(first.algorithm, second.algorithm));
        body.put("engine", useNeo4j ? "NEO4J_GDS" : "MEMORY_OSM");
        putTrafficMeta(body);
        body.put("waypoints", Map.of(
                "rider", Map.of("lat", riderLat, "lon", riderLon),
                "merchant", Map.of("lat", merchantLat, "lon", merchantLon),
                "user", Map.of("lat", userLat, "lon", userLon)
        ));
        return body;
    }

    private void putTrafficMeta(Map<String, Object> body) {
        body.put("congestionApplied", true);
        body.put("trafficSource", trafficSource);
        body.put("trafficHint", trafficHint());
    }

    private String trafficHint() {
        return TrafficProfile.hint(trafficSource, trafficPeriod);
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
        body.put("segments", graph.segments(path));
        body.put("cost", path.cost);
        body.put("algorithm", path.algorithm);
        return body;
    }
}

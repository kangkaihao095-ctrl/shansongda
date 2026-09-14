package com.shansuda.dispatch.service;

import com.shansuda.common.api.BizException;
import com.shansuda.common.auth.AuthHolder;
import com.shansuda.common.auth.AuthUser;
import com.shansuda.common.route.AmapTrafficClient;
import com.shansuda.dispatch.client.AccountClient;
import com.shansuda.dispatch.client.ActivityClient;
import com.shansuda.dispatch.client.OrderClient;
import com.shansuda.common.route.GridPathFinder;
import com.shansuda.common.route.Neo4jRoadStore;
import com.shansuda.common.route.OsmGraphLoader;
import com.shansuda.common.route.TrafficProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DispatchService {

    private static final Logger log = LoggerFactory.getLogger(DispatchService.class);

    private final GridPathFinder graph;
    private final ObjectProvider<OrderClient> orderClient;
    private final ObjectProvider<ActivityClient> activityClient;
    private final ObjectProvider<AccountClient> accountClient;
    private final String mode;
    private final String neo4jUri;
    private final String neo4jUser;
    private final String neo4jPassword;
    private final String amapKey;
    private final Clock clock;
    private Neo4jRoadStore neo4j;
    private boolean useNeo4j;
    private volatile String trafficSource = TrafficProfile.DEFAULT;
    private volatile String trafficPeriod = "";
    private volatile boolean amapEverSucceeded;

    public DispatchService(ObjectProvider<OrderClient> orderClient,
                           ObjectProvider<ActivityClient> activityClient, ObjectProvider<AccountClient> accountClient,
                           @Value("${ssd.mode:auto}") String mode,
                           @Value("${ssd.neo4j.uri:bolt://127.0.0.1:7687}") String neo4jUri,
                           @Value("${ssd.neo4j.username:neo4j}") String neo4jUser,
                           @Value("${ssd.neo4j.password:shansuda}") String neo4jPassword,
                           @Value("${ssd.amap.key:}") String amapKey) {
        this.graph = OsmGraphLoader.load();
        this.orderClient = orderClient;
        this.activityClient = activityClient;
        this.accountClient = accountClient;
        this.mode = mode;
        this.neo4jUri = neo4jUri;
        this.neo4jUser = neo4jUser;
        this.neo4jPassword = neo4jPassword;
        this.amapKey = amapKey == null ? "" : amapKey.trim();
        this.clock = Clock.system(TrafficProfile.ZONE);
    }

    @PostConstruct
    public void init() {
        log.info("调度路网就绪：节点 {} 边 {}", graph.nodes().size(), graph.edgeCount());
        if ("dry-run".equals(mode)) {
            return;
        }
        if (amapKey.isEmpty()) {
            refreshCongestion();
        }
        try {
            neo4j = new Neo4jRoadStore(neo4jUri, neo4jUser, neo4jPassword);
            if (neo4j.ping()) {
                neo4j.importGrid(graph);
                useNeo4j = true;
                log.info("Neo4j 路网已导入，规划走 GDS A*（缺坐标降级 Dijkstra）");
            }
        } catch (Exception ex) {
            log.warn("Neo4j 初始化失败，使用内存路网: {}", ex.getMessage());
            useNeo4j = false;
        }
        if (!amapKey.isEmpty()) {
            refreshCongestion();
        }
    }

    @PreDestroy
    public void shutdown() {
        if (neo4j != null) {
            neo4j.close();
        }
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
        body.put("congestionApplied", true);
        body.put("trafficSource", trafficSource);
        body.put("trafficHint", TrafficProfile.hint(trafficSource, trafficPeriod));
        body.put("waypoints", Map.of(
                "rider", Map.of("lat", riderLat, "lon", riderLon),
                "merchant", Map.of("lat", merchantLat, "lon", merchantLon),
                "user", Map.of("lat", userLat, "lon", userLon)
        ));
        return body;
    }

    public Map<String, Object> assign(long orderId) {
        AuthUser auth = AuthHolder.require();
        if (!auth.isMerchant()) {
            throw BizException.forbidden("仅商家可自动指派");
        }
        OrderClient orders = orderClient.getObject();
        Map<String, Object> order = orders.get(orderId).data();
        if (order == null || !"PAID".equals(String.valueOf(order.get("status")))) {
            throw BizException.conflict("ILLEGAL_STATE", "仅已支付订单可指派");
        }
        double mlat = ((Number) order.get("merchantLat")).doubleValue();
        double mlon = ((Number) order.get("merchantLon")).doubleValue();
        double ulat = ((Number) order.get("userLat")).doubleValue();
        double ulon = ((Number) order.get("userLon")).doubleValue();
        List<Map<String, Object>> riders = accountClient.getObject()
                .nearby(mlat, mlon, 5000, "ONLINE", "IDLE").data();
        if (riders == null || riders.isEmpty()) {
            throw BizException.notFound("附近无空闲骑手");
        }
        Map<String, Object> best = null;
        double bestCost = Double.MAX_VALUE;
        Map<String, Object> bestRoute = null;
        for (Map<String, Object> rider : riders) {
            double rlat = ((Number) rider.get("lat")).doubleValue();
            double rlon = ((Number) rider.get("lon")).doubleValue();
            Map<String, Object> planned = route(rlat, rlon, mlat, mlon, ulat, ulon);
            double cost = ((Number) planned.get("cost")).doubleValue();
            if (cost < bestCost) {
                bestCost = cost;
                best = rider;
                bestRoute = planned;
            }
        }
        long riderId = ((Number) best.get("riderId")).longValue();
        activityClient.getObject().occupy(Map.of("orderId", orderId, "riderId", riderId));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("orderId", orderId);
        body.put("riderId", riderId);
        body.put("route", bestRoute);
        return body;
    }

    @Scheduled(initialDelay = 0, fixedDelayString = "${ssd.dispatch.congestion-ms:60000}")
    public void refreshCongestion() {
        AmapTrafficClient.TrafficSnapshot snap = AmapTrafficClient.fetch(graph, amapKey);
        TrafficProfile.Decision decision = TrafficProfile.refresh(graph, snap, clock, amapEverSucceeded);
        trafficSource = decision.source();
        trafficPeriod = decision.periodLabel() == null ? "" : decision.periodLabel();
        if (TrafficProfile.AMAP.equals(decision.source())) {
            amapEverSucceeded = true;
        }
        if (!decision.wrote()) {
            log.warn("高德态势失败，保留上一轮边权: {}", snap.error());
            return;
        }
        if (TrafficProfile.PROFILE.equals(decision.source())) {
            log.info("时段路况画像已写入边权：period={} 变更 {}", trafficPeriod, decision.rows().size());
        } else {
            log.info("高德态势已写入边权：变更 {}", decision.rows().size());
        }
        if (useNeo4j && neo4j != null && !decision.rows().isEmpty()) {
            try {
                neo4j.updateCongestion(decision.rows());
            } catch (Exception ex) {
                log.warn("拥堵更新失败: {}", ex.getMessage());
            }
        }
    }

    private GridPathFinder.Path segment(double fromLat, double fromLon, double toLat, double toLon) {
        GridPathFinder.Node src = graph.nearest(fromLat, fromLon);
        GridPathFinder.Node dst = graph.nearest(toLat, toLon);
        boolean astar = src.hasCoord() && dst.hasCoord();
        if (useNeo4j && neo4j != null) {
            try {
                return neo4j.shortest(src.id, dst.id, astar);
            } catch (Exception ex) {
                log.warn("GDS 规划失败，降级内存: {}", ex.getMessage());
            }
        }
        try {
            return graph.shortest(src.id, dst.id);
        } catch (IllegalStateException ex) {
            log.warn("路网暂不可达，退回 Haversine 直线: {}", ex.getMessage());
            double km = GridPathFinder.haversineKm(src.lat, src.lon, dst.lat, dst.lon);
            return new GridPathFinder.Path(List.of(src.id, dst.id), Math.max(0.2, km * 2.2), "HAVERSINE_FALLBACK");
        }
    }

    static String combineAlgorithm(String first, String second) {
        if ("HAVERSINE_FALLBACK".equals(first) || "HAVERSINE_FALLBACK".equals(second)) {
            return "HAVERSINE_FALLBACK";
        }
        return first;
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

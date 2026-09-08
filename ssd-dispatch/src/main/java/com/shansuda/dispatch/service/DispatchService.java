package com.shansuda.dispatch.service;

import com.shansuda.common.api.BizException;
import com.shansuda.common.auth.AuthHolder;
import com.shansuda.common.auth.AuthUser;
import com.shansuda.dispatch.client.AccountClient;
import com.shansuda.dispatch.client.ActivityClient;
import com.shansuda.dispatch.client.OrderClient;
import com.shansuda.dispatch.grid.CongestionAggregator;
import com.shansuda.dispatch.neo4j.Neo4jRoadStore;
import com.shansuda.common.route.GridPathFinder;
import com.shansuda.common.route.OsmGraphLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
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
    private Neo4jRoadStore neo4j;
    private boolean useNeo4j;

    public DispatchService(ObjectProvider<OrderClient> orderClient,
                           ObjectProvider<ActivityClient> activityClient, ObjectProvider<AccountClient> accountClient,
                           @Value("${ssd.mode:auto}") String mode,
                           @Value("${ssd.neo4j.uri:bolt://127.0.0.1:7687}") String neo4jUri,
                           @Value("${ssd.neo4j.username:neo4j}") String neo4jUser,
                           @Value("${ssd.neo4j.password:shansuda}") String neo4jPassword) {
        this.graph = OsmGraphLoader.load();
        this.orderClient = orderClient;
        this.activityClient = activityClient;
        this.accountClient = accountClient;
        this.mode = mode;
        this.neo4jUri = neo4jUri;
        this.neo4jUser = neo4jUser;
        this.neo4jPassword = neo4jPassword;
    }

    @PostConstruct
    public void init() {
        log.info("调度路网就绪：节点 {} 边 {}", graph.nodes().size(), graph.edgeCount());
        if ("dry-run".equals(mode)) {
            return;
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
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("riderToMerchant", leg(first));
        body.put("merchantToUser", leg(second));
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

    @Scheduled(fixedDelayString = "${ssd.dispatch.congestion-ms:60000}")
    public void refreshCongestion() {
        List<double[]> positions = loadRiderPositions();
        boolean seeded = positions.isEmpty();
        if (seeded) {
            positions = new ArrayList<>(CongestionAggregator.seedTraces());
        }
        List<Map<String, Object>> rows = CongestionAggregator.apply(graph, positions);
        if (rows.isEmpty()) {
            return;
        }
        log.debug("拥堵聚合：位置 {} 条{}，更新边 {}", positions.size(), seeded ? "（种子轨迹）" : "", rows.size());
        if (useNeo4j && neo4j != null) {
            try {
                neo4j.updateCongestion(rows);
            } catch (Exception ex) {
                log.warn("拥堵更新失败: {}", ex.getMessage());
            }
        }
    }

    private List<double[]> loadRiderPositions() {
        List<double[]> positions = new ArrayList<>();
        AccountClient account = accountClient.getIfAvailable();
        if (account == null) {
            return positions;
        }
        try {
            List<Map<String, Object>> rows = account.riderLocations(300).data();
            if (rows == null) {
                return positions;
            }
            for (Map<String, Object> row : rows) {
                if (row.get("lat") instanceof Number lat && row.get("lon") instanceof Number lon) {
                    positions.add(new double[]{lat.doubleValue(), lon.doubleValue()});
                }
            }
        } catch (Exception ex) {
            log.debug("读取骑手位置失败，将用种子轨迹: {}", ex.getMessage());
        }
        return positions;
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

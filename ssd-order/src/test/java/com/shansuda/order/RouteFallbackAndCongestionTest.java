package com.shansuda.order;

import com.shansuda.common.route.AmapTrafficClient;
import com.shansuda.common.route.CongestionAggregator;
import com.shansuda.common.route.GridPathFinder;
import com.shansuda.order.route.RouteService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RouteFallbackAndCongestionTest {

    @Test
    void haversineFallbackIsNotFakeAstar() {
        assertEquals("HAVERSINE_FALLBACK",
                RouteService.combineAlgorithm("HAVERSINE_FALLBACK", "ASTAR"));
        assertEquals("ASTAR", RouteService.combineAlgorithm("ASTAR", "ASTAR"));
        GridPathFinder.Path fallback = new GridPathFinder.Path(List.of(1, 2), 1.5, RouteService.HAVERSINE_FALLBACK);
        assertEquals("HAVERSINE_FALLBACK", fallback.algorithm);
    }

    @Test
    void unreachableMarksHaversineFallbackNotAstar() {
        GridPathFinder graph = new GridPathFinder();
        graph.addNode(new GridPathFinder.Node(1, 31.230, 121.470));
        graph.addNode(new GridPathFinder.Node(2, 31.240, 121.485));
        RouteService svc = RouteService.withGraph(graph);
        Map<String, Object> body = svc.route(31.230, 121.470, 31.240, 121.485, 31.240, 121.485);
        assertEquals("HAVERSINE_FALLBACK", body.get("algorithm"));
        @SuppressWarnings("unchecked")
        Map<String, Object> first = (Map<String, Object>) body.get("riderToMerchant");
        assertEquals("HAVERSINE_FALLBACK", first.get("algorithm"));
    }

    @Test
    void congestionChangesEdgeWeight() {
        GridPathFinder graph = GridPathFinder.demoGrid();
        GridPathFinder.Edge edge = graph.adj().get(0).get(0);
        double before = edge.cost;
        GridPathFinder.Node a = graph.node(0);
        CongestionAggregator.apply(graph, List.of(
                new double[]{a.lat, a.lon},
                new double[]{a.lat, a.lon},
                new double[]{a.lat, a.lon}
        ));
        assertTrue(graph.adj().get(0).stream().anyMatch(e -> e.congestion > 1.01));
        assertTrue(graph.adj().get(0).stream().anyMatch(e -> e.cost > before - 1e-9 && e.congestion > 1.0));
    }

    @Test
    void pathSegmentsCarryMixedCongestion() {
        GridPathFinder graph = GridPathFinder.demoGrid();
        GridPathFinder.Node a = graph.node(0);
        CongestionAggregator.apply(graph, List.of(
                new double[]{a.lat, a.lon},
                new double[]{a.lat, a.lon},
                new double[]{a.lat, a.lon}
        ));
        GridPathFinder.Path path = graph.shortest(0, 9);
        List<Map<String, Object>> segs = graph.segments(path);
        assertTrue(segs.size() >= 2);
        Set<Double> values = new HashSet<>();
        for (Map<String, Object> seg : segs) {
            assertNotNull(seg.get("from"));
            assertNotNull(seg.get("to"));
            values.add(((Number) seg.get("congestion")).doubleValue());
        }
        assertTrue(values.size() >= 2);
        assertTrue(values.stream().anyMatch(v -> v > 1.01));
    }

    @Test
    void routeJsonExposesPerEdgeCongestion() {
        GridPathFinder graph = com.shansuda.common.route.OsmGraphLoader.load();
        RouteService svc = RouteService.withGraph(graph);
        Map<String, Object> cold = svc.route(31.2304, 121.4737, 31.2380, 121.4840, 31.2240, 121.4690);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> coldSegs = (List<Map<String, Object>>) cold.get("segments");
        assertTrue(coldSegs.size() >= 6);
        List<AmapTrafficClient.AmapRoad> roads = new ArrayList<>();
        int[] idx = {1, coldSegs.size() / 3, coldSegs.size() * 2 / 3};
        String[] status = {"2", "3", "4"};
        for (int k = 0; k < idx.length; k++) {
            Map<String, Object> seg = coldSegs.get(Math.min(idx[k], coldSegs.size() - 1));
            @SuppressWarnings("unchecked")
            Map<String, Object> from = (Map<String, Object>) seg.get("from");
            @SuppressWarnings("unchecked")
            Map<String, Object> to = (Map<String, Object>) seg.get("to");
            String poly = from.get("lon") + "," + from.get("lat") + ";" + to.get("lon") + "," + to.get("lat");
            roads.add(new AmapTrafficClient.AmapRoad("", status[k], null, poly));
        }
        AmapTrafficClient.apply(graph, roads);
        Map<String, Object> body = svc.route(31.2304, 121.4737, 31.2380, 121.4840, 31.2240, 121.4690);
        assertEquals(Boolean.TRUE, body.get("congestionApplied"));
        assertEquals("MEMORY_OSM", body.get("engine"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> segs = (List<Map<String, Object>>) body.get("segments");
        assertNotNull(segs);
        assertTrue(segs.size() >= 2);
        Set<Double> values = new HashSet<>();
        for (Map<String, Object> seg : segs) {
            values.add(((Number) seg.get("congestion")).doubleValue());
        }
        boolean mixed = values.size() >= 2 && values.stream().anyMatch(v -> v > 1.01);
        boolean rerouted = !cold.get("cost").equals(body.get("cost"));
        assertTrue(mixed || rerouted, "拥堵边应出现在折线上，或 A* 已改道避开");
        if (mixed) {
            assertTrue(values.stream().anyMatch(v -> v > 1.01), "至少两条边 congestion 不同，供绿黄红着色");
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> legs = (List<Map<String, Object>>) body.get("legs");
        assertNotNull(legs);
        assertEquals(2, legs.size());
        @SuppressWarnings("unchecked")
        Map<String, Object> firstLeg = (Map<String, Object>) body.get("riderToMerchant");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> firstSegs = (List<Map<String, Object>>) firstLeg.get("segments");
        assertTrue(firstSegs != null && !firstSegs.isEmpty());
    }

    @Test
    void amapFailureKeepsLastWeightsAndDoesNotUseRandom() {
        GridPathFinder graph = GridPathFinder.demoGrid();
        GridPathFinder.Node a = graph.node(0);
        GridPathFinder.Node b = graph.node(1);
        RouteService svc = RouteService.withGraph(graph);
        svc.applyTrafficSnapshot(AmapTrafficClient.TrafficSnapshot.success(List.of(
                new AmapTrafficClient.AmapRoad("", "4", 8.0, a.lon + "," + a.lat + ";" + b.lon + "," + b.lat)
        ), 1));
        double jammed = graph.adj().get(0).get(0).congestion;
        assertTrue(jammed > 1.6);
        assertEquals("AMAP", svc.trafficSource());
        Map<String, Object> ok = svc.route(a.lat, a.lon, b.lat, b.lon, b.lat, b.lon);
        assertEquals("AMAP", ok.get("trafficSource"));
        assertTrue(String.valueOf(ok.get("trafficHint")).contains("高德"));

        svc.applyTrafficSnapshot(AmapTrafficClient.TrafficSnapshot.fail("HTTP 500"));
        assertEquals(jammed, graph.adj().get(0).get(0).congestion, 1e-9);
        assertEquals("LAST_SUCCESS", svc.trafficSource());
        Map<String, Object> body = svc.route(a.lat, a.lon, b.lat, b.lon, b.lat, b.lon);
        assertEquals("LAST_SUCCESS", body.get("trafficSource"));
        assertTrue(String.valueOf(body.get("trafficHint")).contains("上次"));
        assertFalse(String.valueOf(body.get("trafficHint")).contains("画像"));
    }

    @Test
    void noKeyUsesProfileNotDefaultOrAmap() {
        Clock morning = Clock.fixed(Instant.parse("2026-09-11T00:00:00Z"), ZoneOffset.UTC);
        GridPathFinder graph = GridPathFinder.demoGrid();
        RouteService svc = RouteService.withGraph(graph, morning);
        svc.applyTrafficSnapshot(AmapTrafficClient.TrafficSnapshot.fail("NO_KEY"));
        assertEquals("PROFILE", svc.trafficSource());
        assertNotEquals("DEFAULT", svc.trafficSource());
        assertNotEquals("AMAP", svc.trafficSource());
        Map<String, Object> body = svc.route(
                graph.node(0).lat, graph.node(0).lon,
                graph.node(1).lat, graph.node(1).lon,
                graph.node(1).lat, graph.node(1).lon);
        assertEquals("PROFILE", body.get("trafficSource"));
        String hint = String.valueOf(body.get("trafficHint"));
        assertTrue(hint.contains("画像"), hint);
        assertTrue(hint.contains("早高峰"), hint);
        assertFalse(hint.contains("真路况"), hint);
        assertFalse(hint.contains("高德态势"), hint);
        assertTrue(graph.adj().get(0).stream().anyMatch(e -> e.congestion > 1.4));
    }

    @Test
    void amapMockSuccessDoesNotMixProfile() {
        Clock morning = Clock.fixed(Instant.parse("2026-09-11T00:00:00Z"), ZoneOffset.UTC);
        GridPathFinder graph = GridPathFinder.demoGrid();
        RouteService svc = RouteService.withGraph(graph, morning);
        svc.applyTrafficSnapshot(AmapTrafficClient.TrafficSnapshot.fail("NO_KEY"));
        assertEquals("PROFILE", svc.trafficSource());
        GridPathFinder.Node a = graph.node(0);
        GridPathFinder.Node b = graph.node(1);
        svc.applyTrafficSnapshot(AmapTrafficClient.TrafficSnapshot.success(List.of(
                new AmapTrafficClient.AmapRoad("", "4", 8.0, a.lon + "," + a.lat + ";" + b.lon + "," + b.lat)
        ), 1));
        assertEquals("AMAP", svc.trafficSource());
        Map<String, Object> body = svc.route(a.lat, a.lon, b.lat, b.lon, b.lat, b.lon);
        assertEquals("AMAP", body.get("trafficSource"));
        assertTrue(String.valueOf(body.get("trafficHint")).contains("高德"));
        assertFalse(String.valueOf(body.get("trafficHint")).contains("画像"));
        assertEquals(2.4, graph.adj().get(0).get(0).congestion, 1e-6);
        long leftover = graph.adj().values().stream().flatMap(List::stream)
                .filter(e -> e.congestion > 1.01 && Math.abs(e.congestion - 2.4) > 1e-6).count();
        assertEquals(0, leftover);
    }

    @Test
    void amapFailNeverSucceededFallsBackToProfile() {
        Clock morning = Clock.fixed(Instant.parse("2026-09-11T00:00:00Z"), ZoneOffset.UTC);
        GridPathFinder graph = GridPathFinder.demoGrid();
        RouteService svc = RouteService.withGraph(graph, morning);
        svc.applyTrafficSnapshot(AmapTrafficClient.TrafficSnapshot.fail("HTTP 500"));
        assertEquals("PROFILE", svc.trafficSource());
        Map<String, Object> body = svc.route(
                graph.node(0).lat, graph.node(0).lon,
                graph.node(1).lat, graph.node(1).lon,
                graph.node(1).lat, graph.node(1).lon);
        assertEquals("PROFILE", body.get("trafficSource"));
        assertTrue(String.valueOf(body.get("trafficHint")).contains("画像"));
        assertNotEquals("HAVERSINE_FALLBACK", body.get("algorithm"));
    }
}

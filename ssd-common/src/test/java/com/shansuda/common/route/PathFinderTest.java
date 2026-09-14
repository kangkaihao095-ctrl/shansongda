package com.shansuda.common.route;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PathFinderTest {

    @Test
    void astarWhenBothHaveCoordinates() {
        GridPathFinder graph = GridPathFinder.demoGrid();
        GridPathFinder.Path path = graph.shortest(0, 9);
        assertEquals("ASTAR", path.algorithm);
        assertTrue(path.nodeIds.size() >= 2);
        assertEquals(0, path.nodeIds.get(0));
        assertEquals(9, path.nodeIds.get(path.nodeIds.size() - 1));
        assertTrue(path.cost > 0);
    }

    @Test
    void dijkstraWhenMissingCoordinates() {
        GridPathFinder graph = new GridPathFinder();
        graph.addNode(new GridPathFinder.Node(1, null, null));
        graph.addNode(new GridPathFinder.Node(2, null, null));
        graph.addNode(new GridPathFinder.Node(3, null, null));
        graph.addEdge(1, new GridPathFinder.Edge(2, 1, 1, "a"));
        graph.addEdge(2, new GridPathFinder.Edge(3, 1, 1, "b"));
        GridPathFinder.Path path = graph.shortest(1, 3);
        assertEquals("DIJKSTRA", path.algorithm);
        assertEquals(List.of(1, 2, 3), path.nodeIds);
        assertEquals(2.0, path.cost);
    }

    @Test
    void shanghaiOsmAstarFollowsRealCoords() {
        GridPathFinder graph = OsmGraphLoader.load();
        assertTrue(graph.nodes().size() >= 50);
        assertTrue(graph.nodes().size() > GridPathFinder.demoGrid().nodes().size(),
                "默认应是 OSM 黄浦摘录，不是十几二十个点的 demo 网格");
        GridPathFinder.Node merchant = graph.nearest(31.2380, 121.4840);
        GridPathFinder.Node user = graph.nearest(31.2240, 121.4690);
        GridPathFinder.Node rider = graph.nearest(31.2304, 121.4737);
        assertTrue(merchant.hasCoord() && user.hasCoord() && rider.hasCoord());
        GridPathFinder.Path toShop = graph.shortest(rider.id, merchant.id);
        GridPathFinder.Path toUser = graph.shortest(merchant.id, user.id);
        assertEquals("ASTAR", toShop.algorithm);
        assertEquals("ASTAR", toUser.algorithm);
        assertTrue(toShop.nodeIds.size() >= 3);
        assertTrue(toUser.nodeIds.size() >= 3);
        assertTrue(toShop.cost > 0 && toUser.cost > 0);
        long named = graph.adj().values().stream().flatMap(List::stream)
                .filter(e -> e.name != null && !e.name.isBlank()).count();
        assertTrue(named > 0, "OSM 边应带路名供高德匹配");
        long graded = graph.adj().values().stream().flatMap(List::stream)
                .filter(e -> e.highway != null && !e.highway.isBlank()).count();
        assertTrue(graded > 0, "OSM 边应带 highway 供时段画像");
    }

    @Test
    void congestionUpdatesEdgeCost() {
        GridPathFinder graph = GridPathFinder.demoGrid();
        double before = graph.adj().get(0).get(0).cost;
        GridPathFinder.Node a = graph.node(0);
        CongestionAggregator.apply(graph, List.of(
                new double[]{a.lat, a.lon},
                new double[]{a.lat, a.lon},
                new double[]{a.lat, a.lon}
        ));
        assertTrue(graph.adj().get(0).stream().anyMatch(e -> e.congestion > 1.01 && e.cost != before));
        GridPathFinder.Path path = graph.shortest(0, 9);
        assertTrue(graph.segments(path).size() >= 2);
        assertTrue(graph.segments(path).stream().anyMatch(s -> ((Number) s.get("congestion")).doubleValue() > 1.01));
    }

    @Test
    void riderOverlayDoesNotBlankTheRest() {
        GridPathFinder graph = GridPathFinder.demoGrid();
        GridPathFinder.Node a = graph.node(0);
        CongestionAggregator.overlay(graph, List.of(new double[]{a.lat, a.lon}));
        assertTrue(graph.adj().get(0).stream().anyMatch(e -> e.congestion > 1.01));
        String hint = CongestionAggregator.hint(graph.segments(graph.shortest(0, 1)));
        assertTrue("缓行".equals(hint) || "拥堵".equals(hint), hint);
    }

    @Test
    void congestionHintLabels() {
        assertEquals("畅通", CongestionAggregator.hint(List.of(Map.of("congestion", 1.0))));
        assertEquals("缓行", CongestionAggregator.hint(List.of(Map.of("congestion", 1.4))));
        assertEquals("拥堵", CongestionAggregator.hint(List.of(Map.of("congestion", 1.7))));
    }
}

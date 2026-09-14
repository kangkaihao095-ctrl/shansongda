package com.shansuda.common.route;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AmapTrafficClientTest {

    @Test
    void blankKeyDoesNotHitNetworkOrMutate() {
        GridPathFinder graph = GridPathFinder.demoGrid();
        double before = graph.adj().get(0).get(0).congestion;
        AmapTrafficClient.TrafficSnapshot snap = AmapTrafficClient.fetch(graph, "");
        assertFalse(snap.ok());
        assertEquals("NO_KEY", snap.error());
        assertEquals(before, graph.adj().get(0).get(0).congestion, 1e-9);
    }

    @Test
    void mapsAmapStatusToCongestion() {
        assertEquals(1.0, AmapTrafficClient.fromStatus("1"));
        assertEquals(1.4, AmapTrafficClient.fromStatus("2"));
        assertEquals(1.85, AmapTrafficClient.fromStatus("3"));
        assertEquals(2.4, AmapTrafficClient.fromStatus("4"));
        assertEquals(1.85, AmapTrafficClient.congestionOf("3", 12.0));
        assertEquals(2.4, AmapTrafficClient.fromSpeed(8.0));
    }

    @Test
    void samplesPolylineLatLon() {
        List<double[]> pts = AmapTrafficClient.samplePolyline("121.48,31.23;121.49,31.24");
        assertEquals(2, pts.size());
        assertEquals(31.23, pts.get(0)[0], 1e-6);
        assertEquals(121.48, pts.get(0)[1], 1e-6);
    }

    @Test
    void tilesHuangpuBelowAmapAreaLimit() {
        List<String> rects = AmapTrafficClient.rectanglesFor(GridPathFinder.demoGrid());
        assertTrue(rects.size() >= 2);
        for (String rect : rects) {
            String[] pair = rect.split(";");
            String[] a = pair[0].split(",");
            String[] b = pair[1].split(",");
            double minLon = Double.parseDouble(a[0]);
            double minLat = Double.parseDouble(a[1]);
            double maxLon = Double.parseDouble(b[0]);
            double maxLat = Double.parseDouble(b[1]);
            double kmLat = (maxLat - minLat) * 110.54;
            double kmLon = (maxLon - minLon) * 111.32 * Math.cos(Math.toRadians((minLat + maxLat) / 2));
            assertTrue(kmLat * kmLon < 10.0, "tile area km2=" + (kmLat * kmLon));
            assertTrue(Math.hypot(kmLat, kmLon) < 10.0);
        }
    }

    @Test
    void parseRoadsAndMatchByNameAndGeometry() throws Exception {
        String json = """
                {"status":"1","info":"OK","trafficinfo":{"roads":[
                  {"name":"南京东路","status":"3","speed":"12.5","polyline":"121.462,31.222;121.464,31.222"},
                  {"name":"西藏中路","status":"1","speed":"42","polyline":"121.470,31.230;121.471,31.231"}
                ]}}
                """;
        var root = new ObjectMapper().readTree(json);
        List<AmapTrafficClient.AmapRoad> roads = AmapTrafficClient.parseRoads(root);
        assertEquals(2, roads.size());
        assertEquals("南京东路", roads.get(0).name());
        assertEquals(12.5, roads.get(0).speed(), 1e-6);

        GridPathFinder graph = new GridPathFinder();
        graph.addNode(new GridPathFinder.Node(0, 31.2220, 121.4620));
        graph.addNode(new GridPathFinder.Node(1, 31.2220, 121.4640));
        graph.addNode(new GridPathFinder.Node(2, 31.2280, 121.4700));
        graph.addEdge(0, new GridPathFinder.Edge(1, 1.0, 1.0, "osm-1-0a", "南京东路"));
        graph.addEdge(1, new GridPathFinder.Edge(0, 1.0, 1.0, "osm-1-0b", "南京东路"));
        graph.addEdge(1, new GridPathFinder.Edge(2, 1.0, 1.0, "osm-2-0a", "侧巷"));
        graph.addEdge(2, new GridPathFinder.Edge(1, 1.0, 1.0, "osm-2-0b", "侧巷"));

        AmapTrafficClient.ApplyResult applied = AmapTrafficClient.apply(graph, roads);
        assertTrue(applied.matchedRoads() >= 1);
        assertEquals(1.85, graph.adj().get(0).get(0).congestion, 1e-6);
        assertEquals(1.85, graph.adj().get(1).stream().filter(e -> e.to == 0).findFirst().orElseThrow().congestion, 1e-6);
        assertEquals(1.0, graph.adj().get(1).stream().filter(e -> e.to == 2).findFirst().orElseThrow().congestion, 1e-6);
        assertTrue(applied.rows().stream().anyMatch(r -> ((Number) r.get("congestion")).doubleValue() > 1.01));
    }

    @Test
    void unmatchedEdgesStaySmoothAndFailureKeepsLast() {
        GridPathFinder graph = GridPathFinder.demoGrid();
        GridPathFinder.Node a = graph.node(0);
        GridPathFinder.Node b = graph.node(1);
        AmapTrafficClient.AmapRoad jam = new AmapTrafficClient.AmapRoad(
                "", "4", 8.0, a.lon + "," + a.lat + ";" + b.lon + "," + b.lat);
        AmapTrafficClient.apply(graph, List.of(jam));
        double jammed = graph.adj().get(0).get(0).congestion;
        assertTrue(jammed > 1.6);

        long smooth = graph.adj().values().stream().flatMap(List::stream).filter(e -> e.congestion <= 1.01).count();
        assertTrue(smooth > 0);

        AmapTrafficClient.TrafficSnapshot fail = AmapTrafficClient.TrafficSnapshot.fail("HTTP 500");
        assertFalse(fail.ok());
        double still = graph.adj().get(0).get(0).congestion;
        assertEquals(jammed, still, 1e-9);
    }

    @Test
    void nameNormalizeStripsDirection() {
        assertEquals("延安东路", AmapTrafficClient.normalizeName("延安东路(中山东一路-河南中路)"));
        assertTrue(AmapTrafficClient.namesOverlap("延安东路", "延安东路"));
    }
}

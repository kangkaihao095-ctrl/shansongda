package com.shansuda.common.route;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

public class GridPathFinder {

    public static final class Node {
        public final int id;
        public final Double lat;
        public final Double lon;

        public Node(int id, Double lat, Double lon) {
            this.id = id;
            this.lat = lat;
            this.lon = lon;
        }

        public boolean hasCoord() {
            return lat != null && lon != null;
        }
    }

    public static final class Edge {
        public final int to;
        public double cost;
        public double baseTime;
        public double congestion;
        public final String roadId;
        /** OSM / 骨架路名，仅内存匹配高德道路与时段画像，不进 Neo4j。 */
        public final String name;
        /** OSM highway，仅内存时段画像使用，不进 Neo4j。 */
        public final String highway;

        public Edge(int to, double baseTime, double congestion, String roadId) {
            this(to, baseTime, congestion, roadId, "", "");
        }

        public Edge(int to, double baseTime, double congestion, String roadId, String name) {
            this(to, baseTime, congestion, roadId, name, "");
        }

        public Edge(int to, double baseTime, double congestion, String roadId, String name, String highway) {
            this.to = to;
            this.baseTime = baseTime;
            this.congestion = congestion;
            this.cost = baseTime * congestion;
            this.roadId = roadId;
            this.name = name == null ? "" : name;
            this.highway = highway == null ? "" : highway;
        }

        public void setCongestion(double congestion) {
            this.congestion = congestion;
            this.cost = baseTime * congestion;
        }
    }

    public static final class Path {
        public final List<Integer> nodeIds;
        public final double cost;
        public final String algorithm;

        public Path(List<Integer> nodeIds, double cost, String algorithm) {
            this.nodeIds = nodeIds;
            this.cost = cost;
            this.algorithm = algorithm;
        }
    }

    private final Map<Integer, Node> nodes = new HashMap<>();
    private final Map<Integer, List<Edge>> adj = new HashMap<>();

    public void addNode(Node node) {
        nodes.put(node.id, node);
        adj.computeIfAbsent(node.id, k -> new ArrayList<>());
    }

    public void addEdge(int from, Edge edge) {
        adj.computeIfAbsent(from, k -> new ArrayList<>()).add(edge);
    }

    public Node node(int id) {
        return nodes.get(id);
    }

    public Map<Integer, Node> nodes() {
        return nodes;
    }

    public Map<Integer, List<Edge>> adj() {
        return adj;
    }

    public int edgeCount() {
        int n = 0;
        for (List<Edge> edges : adj.values()) {
            n += edges.size();
        }
        return n;
    }

    /** 把经纬度映射到最近路段（点到折线距离），供拥堵聚合使用。 */
    public Edge edgeBetween(int from, int to) {
        for (Edge edge : adj.getOrDefault(from, List.of())) {
            if (edge.to == to) {
                return edge;
            }
        }
        return null;
    }

    /** 路径折线分段，带当前边权 congestion，供前端绿/黄/红着色。 */
    public List<Map<String, Object>> segments(Path path) {
        List<Map<String, Object>> segs = new ArrayList<>();
        if (path == null || path.nodeIds == null || path.nodeIds.size() < 2) {
            return segs;
        }
        for (int i = 0; i < path.nodeIds.size() - 1; i++) {
            int fromId = path.nodeIds.get(i);
            int toId = path.nodeIds.get(i + 1);
            Node from = node(fromId);
            Node to = node(toId);
            if (from == null || to == null || !from.hasCoord() || !to.hasCoord()) {
                continue;
            }
            Edge edge = edgeBetween(fromId, toId);
            Map<String, Object> seg = new LinkedHashMap<>();
            seg.put("from", Map.of("lat", from.lat, "lon", from.lon));
            seg.put("to", Map.of("lat", to.lat, "lon", to.lon));
            double congestion = edge != null ? edge.congestion : 1.0;
            seg.put("congestion", congestion);
            if (edge != null) {
                seg.put("cost", edge.cost);
                seg.put("roadId", edge.roadId);
            }
            segs.add(seg);
        }
        return segs;
    }

    public Edge nearestEdge(double lat, double lon) {
        Edge best = null;
        double bestD = Double.MAX_VALUE;
        for (Map.Entry<Integer, List<Edge>> entry : adj.entrySet()) {
            Node from = nodes.get(entry.getKey());
            if (from == null || !from.hasCoord()) {
                continue;
            }
            for (Edge edge : entry.getValue()) {
                Node to = nodes.get(edge.to);
                if (to == null || !to.hasCoord()) {
                    continue;
                }
                double d = pointToSegmentMeters(lat, lon, from.lat, from.lon, to.lat, to.lon);
                if (d < bestD) {
                    bestD = d;
                    best = edge;
                }
            }
        }
        return best;
    }

    public static double pointToSegmentMeters(double lat, double lon,
                                              double lat1, double lon1, double lat2, double lon2) {
        double midLat = Math.toRadians((lat1 + lat2) / 2.0);
        double x = (lon - lon1) * Math.cos(midLat) * 111_320;
        double y = (lat - lat1) * 110_540;
        double x2 = (lon2 - lon1) * Math.cos(midLat) * 111_320;
        double y2 = (lat2 - lat1) * 110_540;
        double len2 = x2 * x2 + y2 * y2;
        double t = len2 < 1e-9 ? 0 : Math.max(0, Math.min(1, (x * x2 + y * y2) / len2));
        return Math.hypot(x - t * x2, y - t * y2);
    }

    public Node nearest(double lat, double lon) {
        Node best = null;
        double bestD = Double.MAX_VALUE;
        for (Node n : nodes.values()) {
            if (!n.hasCoord()) {
                continue;
            }
            double d = (n.lat - lat) * (n.lat - lat) + (n.lon - lon) * (n.lon - lon);
            if (d < bestD) {
                bestD = d;
                best = n;
            }
        }
        return best;
    }

    public Path shortest(int src, int dst) {
        Node s = nodes.get(src);
        Node t = nodes.get(dst);
        if (s == null || t == null) {
            throw new IllegalArgumentException("节点不存在");
        }
        if (s.hasCoord() && t.hasCoord()) {
            return search(src, dst, true);
        }
        return search(src, dst, false);
    }

    private Path search(int src, int dst, boolean astar) {
        record State(int id, double f) {
        }
        Map<Integer, Double> gScore = new HashMap<>();
        Map<Integer, Integer> prev = new HashMap<>();
        PriorityQueue<State> open = new PriorityQueue<>(Comparator.comparingDouble(State::f));
        gScore.put(src, 0.0);
        open.add(new State(src, heuristic(src, dst, astar)));
        while (!open.isEmpty()) {
            State cur = open.poll();
            if (cur.id == dst) {
                return new Path(rebuild(prev, dst), gScore.get(dst), astar ? "ASTAR" : "DIJKSTRA");
            }
            double g = gScore.getOrDefault(cur.id, Double.POSITIVE_INFINITY);
            if (cur.f > g + heuristic(cur.id, dst, astar) + 1e-9) {
                continue;
            }
            for (Edge e : adj.getOrDefault(cur.id, List.of())) {
                double ng = g + e.cost;
                if (ng < gScore.getOrDefault(e.to, Double.POSITIVE_INFINITY)) {
                    gScore.put(e.to, ng);
                    prev.put(e.to, cur.id);
                    open.add(new State(e.to, ng + heuristic(e.to, dst, astar)));
                }
            }
        }
        throw new IllegalStateException("不可达");
    }

    private double heuristic(int from, int to, boolean astar) {
        if (!astar) {
            return 0;
        }
        Node a = nodes.get(from);
        Node b = nodes.get(to);
        if (a == null || b == null || !a.hasCoord() || !b.hasCoord()) {
            return 0;
        }
        return haversineKm(a.lat, a.lon, b.lat, b.lon) * 2.0;
    }

    private List<Integer> rebuild(Map<Integer, Integer> prev, int dst) {
        List<Integer> path = new ArrayList<>();
        Integer cur = dst;
        while (cur != null) {
            path.add(cur);
            cur = prev.get(cur);
        }
        Collections.reverse(path);
        return path;
    }

    public static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double r = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * r * Math.asin(Math.min(1, Math.sqrt(a)));
    }

    public static GridPathFinder demoGrid() {
        GridPathFinder graph = new GridPathFinder();
        int n = 10;
        double originLat = 31.2200;
        double originLon = 121.4600;
        double step = 0.002;
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                int id = y * n + x;
                graph.addNode(new Node(id, originLat + y * step, originLon + x * step));
            }
        }
        int road = 0;
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                int id = y * n + x;
                if (x + 1 < n) {
                    String highway = y == 0 || y == 4 ? "primary" : "residential";
                    String name = y == 0 ? "南京东路" : (y == 4 ? "延安路" : "");
                    link(graph, id, id + 1, road++, highway, name);
                }
                if (y + 1 < n) {
                    String highway = x == n - 1 ? "primary" : "residential";
                    String name = x == n - 1 ? "中山东一路" : "";
                    link(graph, id, id + n, road++, highway, name);
                }
            }
        }
        return graph;
    }

    private static void link(GridPathFinder graph, int a, int b, int road, String highway, String name) {
        Node na = graph.node(a);
        Node nb = graph.node(b);
        double km = haversineKm(na.lat, na.lon, nb.lat, nb.lon);
        double base = Math.max(0.4, km * 2.0);
        graph.addEdge(a, new Edge(b, base, 1.0, "r" + road + "a", name, highway));
        graph.addEdge(b, new Edge(a, base, 1.0, "r" + road + "b", name, highway));
    }
}

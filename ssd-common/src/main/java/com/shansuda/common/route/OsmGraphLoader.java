package com.shansuda.common.route;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 解析 OSM XML 为路口/路段图。主键重映射为连续 int，便于 Neo4j Intersection.id。
 */
public final class OsmGraphLoader {

    private static final Logger log = LoggerFactory.getLogger(OsmGraphLoader.class);
    public static final String CLASSPATH = "/osm/shanghai-huangpu.osm";
    private static final Set<String> HIGHWAYS = Set.of(
            "motorway", "trunk", "primary", "secondary", "tertiary",
            "unclassified", "residential", "living_street", "pedestrian", "service");

    private OsmGraphLoader() {
    }

    public static GridPathFinder load() {
        InputStream in = OsmGraphLoader.class.getResourceAsStream(CLASSPATH);
        if (in != null) {
            try (in) {
                GridPathFinder graph = parse(in);
                if (graph.nodes().size() >= 50) {
                    log.info("已加载 OSM 黄浦路网：节点 {} 边 {}", graph.nodes().size(), graph.edgeCount());
                    return graph;
                }
                log.warn("OSM 路网过稀（{} 节点），改用黄浦骨架", graph.nodes().size());
            } catch (Exception ex) {
                log.warn("解析 OSM 失败，改用黄浦骨架: {}", ex.getMessage());
            }
        } else {
            log.warn("classpath 无 {}，改用黄浦骨架", CLASSPATH);
        }
        return fallbackShanghai();
    }

    public static GridPathFinder loadFile(Path path) throws Exception {
        try (InputStream in = Files.newInputStream(path)) {
            return parse(in);
        }
    }

    public static GridPathFinder parse(InputStream in) throws Exception {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        XMLStreamReader reader = factory.createXMLStreamReader(in);
        Map<Long, double[]> nodes = new HashMap<>();
        List<OsmWay> ways = new ArrayList<>();
        OsmWay current = null;
        try {
            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT) {
                    String name = reader.getLocalName();
                    if ("node".equals(name)) {
                        long id = Long.parseLong(reader.getAttributeValue(null, "id"));
                        double lat = Double.parseDouble(reader.getAttributeValue(null, "lat"));
                        double lon = Double.parseDouble(reader.getAttributeValue(null, "lon"));
                        nodes.put(id, new double[]{lat, lon});
                    } else if ("way".equals(name)) {
                        current = new OsmWay(Long.parseLong(reader.getAttributeValue(null, "id")));
                    } else if (current != null && "nd".equals(name)) {
                        current.nds.add(Long.parseLong(reader.getAttributeValue(null, "ref")));
                    } else if (current != null && "tag".equals(name)) {
                        String k = reader.getAttributeValue(null, "k");
                        String v = reader.getAttributeValue(null, "v");
                        if ("highway".equals(k)) {
                            current.highway = v;
                        } else if ("oneway".equals(k)) {
                            current.oneway = v;
                        } else if ("name".equals(k) || "name:zh".equals(k) || "name:zh-Hans".equals(k)) {
                            if (current.name == null || current.name.isBlank() || "name".equals(k)) {
                                current.name = v;
                            }
                        }
                    }
                } else if (event == XMLStreamConstants.END_ELEMENT && "way".equals(reader.getLocalName())) {
                    if (current != null && current.nds.size() >= 2 && HIGHWAYS.contains(current.highway)) {
                        ways.add(current);
                    }
                    current = null;
                }
            }
        } finally {
            reader.close();
        }
        return build(nodes, ways);
    }

    private static GridPathFinder build(Map<Long, double[]> osmNodes, List<OsmWay> ways) {
        Set<Long> used = new HashSet<>();
        for (OsmWay way : ways) {
            used.addAll(way.nds);
        }
        Map<Long, Integer> remap = new HashMap<>();
        GridPathFinder graph = new GridPathFinder();
        int seq = 0;
        for (Long osmId : used) {
            double[] ll = osmNodes.get(osmId);
            if (ll == null) {
                continue;
            }
            remap.put(osmId, seq);
            graph.addNode(new GridPathFinder.Node(seq, ll[0], ll[1]));
            seq++;
        }
        Set<String> seen = new HashSet<>();
        int road = 0;
        for (OsmWay way : ways) {
            for (int i = 0; i + 1 < way.nds.size(); i++) {
                Integer a = remap.get(way.nds.get(i));
                Integer b = remap.get(way.nds.get(i + 1));
                if (a == null || b == null || a.equals(b)) {
                    continue;
                }
                GridPathFinder.Node na = graph.node(a);
                GridPathFinder.Node nb = graph.node(b);
                double km = GridPathFinder.haversineKm(na.lat, na.lon, nb.lat, nb.lon);
                double base = Math.max(0.12, km * 2.2);
                // DEV_TASK：ROAD 双向各一条；骑手可逆行，忽略 OSM oneway 以免图不连通
                String roadName = way.name == null ? "" : way.name;
                String highway = way.highway == null ? "" : way.highway;
                if (seen.add(a + ">" + b)) {
                    graph.addEdge(a, new GridPathFinder.Edge(b, base, 1.0, "osm-" + way.id + "-" + i + "a", roadName, highway));
                    road++;
                }
                if (seen.add(b + ">" + a)) {
                    graph.addEdge(b, new GridPathFinder.Edge(a, base, 1.0, "osm-" + way.id + "-" + i + "b", roadName, highway));
                    road++;
                }
            }
        }
        if (graph.nodes().isEmpty() || road == 0) {
            throw new IllegalStateException("OSM 未解析出可用路段");
        }
        return keepLargestComponent(graph);
    }

    /** 丢掉河心/广场孤立碎片，只保留最大连通片，避免 nearest 吸到死节点。 */
    static GridPathFinder keepLargestComponent(GridPathFinder raw) {
        Map<Integer, List<Integer>> undirected = new HashMap<>();
        raw.adj().forEach((from, edges) -> {
            for (GridPathFinder.Edge e : edges) {
                undirected.computeIfAbsent(from, k -> new ArrayList<>()).add(e.to);
                undirected.computeIfAbsent(e.to, k -> new ArrayList<>()).add(from);
            }
        });
        Set<Integer> best = Set.of();
        Set<Integer> seen = new HashSet<>();
        for (int start : raw.nodes().keySet()) {
            if (!seen.add(start)) {
                continue;
            }
            Set<Integer> comp = new HashSet<>();
            ArrayList<Integer> stack = new ArrayList<>();
            stack.add(start);
            while (!stack.isEmpty()) {
                int cur = stack.remove(stack.size() - 1);
                if (!comp.add(cur)) {
                    continue;
                }
                seen.add(cur);
                for (int n : undirected.getOrDefault(cur, List.of())) {
                    if (!comp.contains(n)) {
                        stack.add(n);
                    }
                }
            }
            if (comp.size() > best.size()) {
                best = comp;
            }
        }
        if (best.size() == raw.nodes().size()) {
            return raw;
        }
        Map<Integer, Integer> remap = new HashMap<>();
        GridPathFinder graph = new GridPathFinder();
        int seq = 0;
        for (int oldId : best) {
            GridPathFinder.Node n = raw.node(oldId);
            remap.put(oldId, seq);
            graph.addNode(new GridPathFinder.Node(seq, n.lat, n.lon));
            seq++;
        }
        Set<String> seenEdge = new HashSet<>();
        raw.adj().forEach((from, edges) -> {
            Integer a = remap.get(from);
            if (a == null) {
                return;
            }
            for (GridPathFinder.Edge e : edges) {
                Integer b = remap.get(e.to);
                if (b == null || !seenEdge.add(a + ">" + b)) {
                    continue;
                }
                graph.addEdge(a, new GridPathFinder.Edge(b, e.baseTime, e.congestion, e.roadId, e.name, e.highway));
            }
        });
        log.info("路网连通片：保留 {} / {} 节点", graph.nodes().size(), raw.nodes().size());
        return graph;
    }

    /**
     * OSM 不可用时的黄浦骨架：人民广场 / 南京东路 / 外滩一带真实坐标，不是假格子。
     */
    public static GridPathFinder fallbackShanghai() {
        GridPathFinder graph = new GridPathFinder();
        double[] lats = {31.2384, 31.2362, 31.2340, 31.2318, 31.2296, 31.2274, 31.2252, 31.2234};
        double[] lons = {121.4690, 121.4737, 121.4776, 121.4814, 121.4840, 121.4872, 121.4904};
        int cols = lons.length;
        int rows = lats.length;
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < cols; x++) {
                graph.addNode(new GridPathFinder.Node(y * cols + x, lats[y], lons[x]));
            }
        }
        int road = 0;
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < cols; x++) {
                int id = y * cols + x;
                if (x + 1 < cols) {
                    link(graph, id, id + 1, road++, cols);
                }
                if (y + 1 < rows) {
                    link(graph, id, id + cols, road++, cols);
                }
            }
        }
        log.info("黄浦骨架路网：节点 {} 边 {}", graph.nodes().size(), graph.edgeCount());
        return graph;
    }

    private static void link(GridPathFinder graph, int a, int b, int road, int cols) {
        GridPathFinder.Node na = graph.node(a);
        GridPathFinder.Node nb = graph.node(b);
        double km = GridPathFinder.haversineKm(na.lat, na.lon, nb.lat, nb.lon);
        double base = Math.max(0.2, km * 2.0);
        int ay = a / cols;
        int ax = a % cols;
        int by = b / cols;
        String highway;
        String name;
        if (ay == by) {
            if (ay == 0) {
                highway = "primary";
                name = "南京东路";
            } else if (ay == 2) {
                highway = "secondary";
                name = "人民大道";
            } else if (ay == 3) {
                highway = "primary";
                name = "延安东路";
            } else {
                highway = "residential";
                name = "";
            }
        } else if (ax == cols - 1) {
            highway = "primary";
            name = "中山东一路";
        } else if (ax == 1) {
            highway = "primary";
            name = "西藏中路";
        } else {
            highway = "tertiary";
            name = "";
        }
        graph.addEdge(a, new GridPathFinder.Edge(b, base, 1.0, "fb-" + road + "a", name, highway));
        graph.addEdge(b, new GridPathFinder.Edge(a, base, 1.0, "fb-" + road + "b", name, highway));
    }

    private static final class OsmWay {
        final long id;
        final List<Long> nds = new ArrayList<>();
        String highway;
        String oneway;
        String name;

        OsmWay(long id) {
            this.id = id;
        }
    }
}

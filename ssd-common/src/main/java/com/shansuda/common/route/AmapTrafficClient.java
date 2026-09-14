package com.shansuda.common.route;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 高德 Web 服务交通态势（矩形）→ OSM 边 congestion/cost。不算驾车 path，失败不改边权。
 */
public final class AmapTrafficClient {

    private static final Logger log = LoggerFactory.getLogger(AmapTrafficClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();
    /** 单块约 2km×2km，低于高德矩形面积 / 对角线限制。 */
    static final double TILE_DEG = 0.018;
    static final double MATCH_METERS = 90;
    static final double NAME_MATCH_METERS = 160;
    static final double CELL_DEG = 0.003;
    private static final String ENDPOINT = "https://restapi.amap.com/v3/traffic/status/rectangle";

    private AmapTrafficClient() {
    }

    public record AmapRoad(String name, String status, Double speed, String polyline) {
    }

    public record TrafficSnapshot(boolean ok, String error, List<AmapRoad> roads, int tilesOk, int tilesFail) {
        public static TrafficSnapshot fail(String error) {
            return new TrafficSnapshot(false, error == null ? "UNKNOWN" : error, List.of(), 0, 0);
        }

        public static TrafficSnapshot fail(String error, int tilesOk, int tilesFail) {
            return new TrafficSnapshot(false, error, List.of(), tilesOk, tilesFail);
        }

        public static TrafficSnapshot success(List<AmapRoad> roads, int tilesOk) {
            return new TrafficSnapshot(true, "", roads == null ? List.of() : roads, tilesOk, 0);
        }
    }

    public record ApplyResult(List<Map<String, Object>> rows, int matchedRoads, int congestedEdges) {
    }

    public static TrafficSnapshot fetch(GridPathFinder graph, String key) {
        if (key == null || key.isBlank()) {
            log.warn("无 SSD_AMAP_KEY，跳过高德交通态势（由调用方改走时段路况画像）");
            return TrafficSnapshot.fail("NO_KEY");
        }
        List<String> rects = rectanglesFor(graph);
        if (rects.isEmpty()) {
            return TrafficSnapshot.fail("NO_BBOX");
        }
        List<AmapRoad> roads = new ArrayList<>();
        int ok = 0;
        int fail = 0;
        String lastError = "";
        for (int i = 0; i < rects.size(); i++) {
            if (i > 0) {
                try {
                    Thread.sleep(40);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    return TrafficSnapshot.fail("INTERRUPTED", ok, fail + 1);
                }
            }
            try {
                JsonNode root = getRectangle(key, rects.get(i));
                if (root.path("status").asInt(0) != 1) {
                    fail++;
                    lastError = root.path("info").asText("AMAP_STATUS");
                    log.warn("高德态势分块失败 info={} infocode={}", lastError, root.path("infocode").asText(""));
                    continue;
                }
                roads.addAll(parseRoads(root));
                ok++;
            } catch (Exception ex) {
                fail++;
                lastError = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
                log.warn("高德态势分块请求失败: {}", lastError);
            }
        }
        if (fail > 0) {
            return TrafficSnapshot.fail("TILE_FAILED:" + fail + "/" + rects.size() + " " + lastError, ok, fail);
        }
        log.info("高德态势拉取成功：分块 {} 道路 {}", ok, roads.size());
        return TrafficSnapshot.success(roads, ok);
    }

    public static ApplyResult apply(GridPathFinder graph, List<AmapRoad> roads) {
        if (graph == null) {
            return new ApplyResult(List.of(), 0, 0);
        }
        List<EdgeRef> refs = indexEdges(graph);
        Map<String, List<EdgeRef>> byName = new HashMap<>();
        Map<Long, List<EdgeRef>> cells = new HashMap<>();
        Map<String, EdgeRef> byRoad = new LinkedHashMap<>();
        for (EdgeRef ref : refs) {
            byRoad.put(ref.edge.roadId, ref);
            String norm = normalizeName(ref.edge.name);
            if (!norm.isEmpty()) {
                byName.computeIfAbsent(norm, k -> new ArrayList<>()).add(ref);
            }
            for (long key : cellKeys(ref.lat1, ref.lon1, ref.lat2, ref.lon2)) {
                cells.computeIfAbsent(key, k -> new ArrayList<>()).add(ref);
            }
        }
        Map<String, Double> target = new HashMap<>();
        int matchedRoads = 0;
        if (roads != null) {
            for (AmapRoad road : roads) {
                if (road == null) {
                    continue;
                }
                double congestion = congestionOf(road.status, road.speed);
                if (congestion <= 1.01) {
                    continue;
                }
                Set<EdgeRef> hits = matchRoad(road, byName, cells);
                if (hits.isEmpty()) {
                    continue;
                }
                matchedRoads++;
                for (EdgeRef hit : hits) {
                    bump(target, hit.edge.roadId, congestion);
                    bump(target, CongestionAggregator.reverseRoadId(hit.edge.roadId), congestion);
                }
            }
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        int congested = 0;
        for (EdgeRef ref : byRoad.values()) {
            double next = target.getOrDefault(ref.edge.roadId, 1.0);
            if (next > 1.01) {
                congested++;
            }
            if (Math.abs(next - ref.edge.congestion) < 1e-6) {
                continue;
            }
            ref.edge.setCongestion(next);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("roadId", ref.edge.roadId);
            row.put("congestion", next);
            rows.add(row);
        }
        log.info("高德态势已映射 OSM 边：拥堵道路 {} 条，拥堵边 {}，边权变更 {}",
                matchedRoads, congested, rows.size());
        return new ApplyResult(rows, matchedRoads, congested);
    }

    static JsonNode getRectangle(String key, String rectangle) throws Exception {
        String url = ENDPOINT + "?key=" + URLEncoder.encode(key, StandardCharsets.UTF_8)
                + "&rectangle=" + URLEncoder.encode(rectangle, StandardCharsets.UTF_8)
                + "&level=6&extensions=all";
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();
        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (res.statusCode() / 100 != 2) {
            throw new IllegalStateException("HTTP " + res.statusCode());
        }
        return MAPPER.readTree(res.body());
    }

    static List<AmapRoad> parseRoads(JsonNode root) {
        List<AmapRoad> out = new ArrayList<>();
        if (root == null) {
            return out;
        }
        JsonNode roads = root.path("trafficinfo").path("roads");
        if (!roads.isArray()) {
            return out;
        }
        for (JsonNode road : roads) {
            Double speed = null;
            JsonNode speedNode = road.get("speed");
            if (speedNode != null && !speedNode.isMissingNode() && !speedNode.isNull() && !speedNode.asText("").isBlank()) {
                try {
                    speed = Double.parseDouble(speedNode.asText().trim());
                } catch (NumberFormatException ignored) {
                    // keep null
                }
            }
            out.add(new AmapRoad(
                    text(road, "name"),
                    text(road, "status"),
                    speed,
                    text(road, "polyline")));
        }
        return out;
    }

    static List<String> rectanglesFor(GridPathFinder graph) {
        double minLat = 31.215;
        double maxLat = 31.250;
        double minLon = 121.460;
        double maxLon = 121.505;
        if (graph != null && !graph.nodes().isEmpty()) {
            minLat = Double.POSITIVE_INFINITY;
            maxLat = Double.NEGATIVE_INFINITY;
            minLon = Double.POSITIVE_INFINITY;
            maxLon = Double.NEGATIVE_INFINITY;
            for (GridPathFinder.Node n : graph.nodes().values()) {
                if (n == null || !n.hasCoord()) {
                    continue;
                }
                minLat = Math.min(minLat, n.lat);
                maxLat = Math.max(maxLat, n.lat);
                minLon = Math.min(minLon, n.lon);
                maxLon = Math.max(maxLon, n.lon);
            }
            if (!Double.isFinite(minLat)) {
                minLat = 31.215;
                maxLat = 31.250;
                minLon = 121.460;
                maxLon = 121.505;
            }
        }
        minLat -= 0.002;
        maxLat += 0.002;
        minLon -= 0.002;
        maxLon += 0.002;
        List<String> rects = new ArrayList<>();
        for (double lat = minLat; lat < maxLat - 1e-9; lat += TILE_DEG) {
            double lat2 = Math.min(maxLat, lat + TILE_DEG);
            for (double lon = minLon; lon < maxLon - 1e-9; lon += TILE_DEG) {
                double lon2 = Math.min(maxLon, lon + TILE_DEG);
                rects.add(formatRect(lon, lat, lon2, lat2));
            }
        }
        return rects;
    }

    static String formatRect(double minLon, double minLat, double maxLon, double maxLat) {
        return minLon + "," + minLat + ";" + maxLon + "," + maxLat;
    }

    static double congestionOf(String status, Double speed) {
        String s = status == null ? "" : status.trim();
        if ("1".equals(s) || "2".equals(s) || "3".equals(s) || "4".equals(s)) {
            return fromStatus(s);
        }
        double fromSpeed = fromSpeed(speed);
        return fromSpeed > 1.01 ? fromSpeed : 1.0;
    }

    static double fromStatus(String status) {
        return switch (status == null ? "" : status.trim()) {
            case "2" -> 1.4;
            case "3" -> 1.85;
            case "4" -> 2.4;
            default -> 1.0;
        };
    }

    static double fromSpeed(Double speed) {
        if (speed == null || speed.isNaN() || speed <= 0) {
            return 1.0;
        }
        if (speed >= 40) {
            return 1.0;
        }
        if (speed >= 25) {
            return 1.4;
        }
        if (speed >= 15) {
            return 1.85;
        }
        return 2.4;
    }

    static List<double[]> samplePolyline(String polyline) {
        List<double[]> pts = new ArrayList<>();
        if (polyline == null || polyline.isBlank()) {
            return pts;
        }
        String[] parts = polyline.split(";");
        int step = parts.length <= 12 ? 1 : Math.max(1, parts.length / 12);
        for (int i = 0; i < parts.length; i += step) {
            addLonLat(pts, parts[i]);
        }
        if (parts.length > 1 && (parts.length - 1) % step != 0) {
            addLonLat(pts, parts[parts.length - 1]);
        }
        return pts;
    }

    static String normalizeName(String name) {
        if (name == null) {
            return "";
        }
        String s = name.trim();
        int cut = s.indexOf('(');
        if (cut < 0) {
            cut = s.indexOf('（');
        }
        if (cut > 0) {
            s = s.substring(0, cut);
        }
        int colon = s.indexOf(':');
        if (colon > 0) {
            s = s.substring(0, colon);
        }
        return s.replace(" ", "").trim();
    }

    static boolean namesOverlap(String amap, String osm) {
        String a = normalizeName(amap);
        String o = normalizeName(osm);
        if (a.isEmpty() || o.isEmpty()) {
            return false;
        }
        if (a.equals(o)) {
            return true;
        }
        return (a.length() >= 2 && o.contains(a)) || (o.length() >= 2 && a.contains(o));
    }

    private static Set<EdgeRef> matchRoad(AmapRoad road, Map<String, List<EdgeRef>> byName,
                                          Map<Long, List<EdgeRef>> cells) {
        List<double[]> samples = samplePolyline(road.polyline);
        String norm = normalizeName(road.name);
        List<EdgeRef> named = new ArrayList<>();
        if (!norm.isEmpty()) {
            List<EdgeRef> exact = byName.get(norm);
            if (exact != null) {
                named.addAll(exact);
            }
            if (named.isEmpty()) {
                byName.forEach((osmName, refs) -> {
                    if (namesOverlap(road.name, osmName)) {
                        named.addAll(refs);
                    }
                });
            }
        }
        Set<EdgeRef> hits = new HashSet<>();
        double limit = named.isEmpty() ? MATCH_METERS : NAME_MATCH_METERS;
        if (!samples.isEmpty()) {
            Set<EdgeRef> cands = new HashSet<>();
            if (!named.isEmpty()) {
                cands.addAll(named);
            } else {
                for (double[] p : samples) {
                    cands.addAll(nearby(cells, p[0], p[1]));
                }
            }
            for (EdgeRef cand : cands) {
                if (!named.isEmpty() && !named.contains(cand) && !namesOverlap(road.name, cand.edge.name)) {
                    continue;
                }
                if (midpointToPolyline(cand, samples) <= limit) {
                    hits.add(cand);
                }
            }
        }
        if (hits.isEmpty() && !named.isEmpty() && !samples.isEmpty()) {
            for (EdgeRef cand : named) {
                if (midpointToPolyline(cand, samples) <= NAME_MATCH_METERS * 1.5) {
                    hits.add(cand);
                }
            }
        }
        if (hits.isEmpty() && samples.isEmpty() && !named.isEmpty()) {
            hits.addAll(named);
        }
        return hits;
    }

    /** 用边中点贴近高德折线，避免路口端点把相交路一并涂上。 */
    private static double midpointToPolyline(EdgeRef edge, List<double[]> line) {
        double midLat = (edge.lat1 + edge.lat2) / 2.0;
        double midLon = (edge.lon1 + edge.lon2) / 2.0;
        double best = Double.MAX_VALUE;
        if (line.size() == 1) {
            return GridPathFinder.pointToSegmentMeters(
                    midLat, midLon, line.get(0)[0], line.get(0)[1], line.get(0)[0], line.get(0)[1]);
        }
        for (int i = 0; i + 1 < line.size(); i++) {
            double d = GridPathFinder.pointToSegmentMeters(
                    midLat, midLon, line.get(i)[0], line.get(i)[1], line.get(i + 1)[0], line.get(i + 1)[1]);
            if (d < best) {
                best = d;
            }
        }
        return best;
    }

    private static List<EdgeRef> nearby(Map<Long, List<EdgeRef>> cells, double lat, double lon) {
        List<EdgeRef> out = new ArrayList<>();
        int y = (int) Math.floor(lat / CELL_DEG);
        int x = (int) Math.floor(lon / CELL_DEG);
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                List<EdgeRef> bucket = cells.get(cellKey(y + dy, x + dx));
                if (bucket != null) {
                    out.addAll(bucket);
                }
            }
        }
        return out;
    }

    private static List<EdgeRef> indexEdges(GridPathFinder graph) {
        List<EdgeRef> refs = new ArrayList<>();
        graph.adj().forEach((fromId, edges) -> {
            GridPathFinder.Node from = graph.node(fromId);
            if (from == null || !from.hasCoord()) {
                return;
            }
            for (GridPathFinder.Edge edge : edges) {
                GridPathFinder.Node to = graph.node(edge.to);
                if (to == null || !to.hasCoord()) {
                    continue;
                }
                refs.add(new EdgeRef(edge, from.lat, from.lon, to.lat, to.lon));
            }
        });
        return refs;
    }

    private static List<Long> cellKeys(double lat1, double lon1, double lat2, double lon2) {
        int y1 = (int) Math.floor(Math.min(lat1, lat2) / CELL_DEG);
        int y2 = (int) Math.floor(Math.max(lat1, lat2) / CELL_DEG);
        int x1 = (int) Math.floor(Math.min(lon1, lon2) / CELL_DEG);
        int x2 = (int) Math.floor(Math.max(lon1, lon2) / CELL_DEG);
        List<Long> keys = new ArrayList<>();
        for (int y = y1; y <= y2; y++) {
            for (int x = x1; x <= x2; x++) {
                keys.add(cellKey(y, x));
            }
        }
        return keys;
    }

    private static long cellKey(int y, int x) {
        return ((long) y << 32) ^ (x & 0xffffffffL);
    }

    private static void bump(Map<String, Double> target, String roadId, double congestion) {
        if (roadId == null || roadId.isBlank()) {
            return;
        }
        target.merge(roadId, congestion, Math::max);
    }

    private static void addLonLat(List<double[]> pts, String part) {
        String[] xy = part.split(",");
        if (xy.length < 2) {
            return;
        }
        try {
            double lon = Double.parseDouble(xy[0].trim());
            double lat = Double.parseDouble(xy[1].trim());
            pts.add(new double[]{lat, lon});
        } catch (NumberFormatException ignored) {
            // skip
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isMissingNode() || v.isNull() || v.isArray() || v.isObject()) {
            return "";
        }
        String s = v.asText("");
        return s == null || "[]".equals(s) ? "" : s;
    }

    private record EdgeRef(GridPathFinder.Edge edge, double lat1, double lon1, double lat2, double lon2) {
    }
}

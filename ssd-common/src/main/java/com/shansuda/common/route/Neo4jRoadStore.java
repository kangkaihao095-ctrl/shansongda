package com.shansuda.common.route;

import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Config;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Session;
import org.neo4j.driver.Values;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 路口=Intersection 节点，道路=ROAD 关系；GDS A*（经纬度启发式），失败降 Dijkstra。
 * 短连接超时，避免本机没起 Neo4j 时拖垮 order 启动。
 */
public class Neo4jRoadStore {

    private static final Logger log = LoggerFactory.getLogger(Neo4jRoadStore.class);
    /** OSM 黄浦片区；保留上一 version 节点便于回滚，GDS 只投影当前 version。 */
    public static final int VERSION = 3;
    private static final int BATCH = 400;

    private final Driver driver;

    public Neo4jRoadStore(String uri, String user, String password) {
        Config.ConfigBuilder builder = Config.builder()
                .withConnectionTimeout(8, TimeUnit.SECONDS)
                .withConnectionAcquisitionTimeout(12, TimeUnit.SECONDS)
                .withMaxConnectionPoolSize(4);
        if (uri != null && (uri.startsWith("bolt://") || uri.startsWith("neo4j://"))) {
            builder.withoutEncryption();
        }
        this.driver = GraphDatabase.driver(uri, AuthTokens.basic(user, password), builder.build());
    }

    public void close() {
        driver.close();
    }

    public boolean ping() {
        try (Session session = driver.session()) {
            session.run("RETURN 1").consume();
            return true;
        } catch (Exception ex) {
            log.warn("Neo4j 不可用，将使用内存路网: {}", ex.getMessage());
            return false;
        }
    }

    public void importGrid(GridPathFinder graph) {
        List<Map<String, Object>> nodes = new ArrayList<>();
        graph.nodes().values().forEach(n -> {
            Map<String, Object> row = new HashMap<>();
            row.put("id", n.id);
            row.put("lat", n.lat);
            row.put("lon", n.lon);
            row.put("version", VERSION);
            nodes.add(row);
        });
        List<Map<String, Object>> roads = new ArrayList<>();
        graph.adj().forEach((from, edges) -> edges.forEach(e -> {
            Map<String, Object> row = new HashMap<>();
            row.put("from", from);
            row.put("to", e.to);
            row.put("roadId", e.roadId);
            row.put("baseTime", e.baseTime);
            row.put("congestion", e.congestion);
            row.put("cost", e.cost);
            row.put("version", VERSION);
            roads.add(row);
        }));
        try (Session session = driver.session()) {
            session.executeWrite(tx -> {
                tx.run("CREATE INDEX intersection_id_ver IF NOT EXISTS FOR (n:Intersection) ON (n.id, n.version)");
                tx.run("CREATE INDEX road_id_ver IF NOT EXISTS FOR ()-[r:ROAD]-() ON (r.roadId, r.version)");
                return null;
            });
            for (List<Map<String, Object>> batch : batches(nodes, BATCH)) {
                List<Map<String, Object>> chunk = new ArrayList<>(batch);
                session.executeWrite(tx -> {
                    tx.run("""
                            UNWIND $rows AS row
                            MERGE (n:Intersection {id: row.id, version: row.version})
                            SET n.lat = row.lat, n.lon = row.lon
                            """, Values.parameters("rows", chunk));
                    return null;
                });
            }
            for (List<Map<String, Object>> batch : batches(roads, BATCH)) {
                List<Map<String, Object>> chunk = new ArrayList<>(batch);
                session.executeWrite(tx -> {
                    tx.run("""
                            UNWIND $rows AS row
                            MATCH (a:Intersection {id: row.from, version: row.version})
                            MATCH (b:Intersection {id: row.to, version: row.version})
                            MERGE (a)-[r:ROAD {roadId: row.roadId, version: row.version}]->(b)
                            SET r.baseTime = row.baseTime, r.congestion = row.congestion, r.cost = row.cost
                            """, Values.parameters("rows", chunk));
                    return null;
                });
            }
            projectGraph(session);
            log.info("Neo4j 路网 version={} 节点 {} 边 {}", VERSION, nodes.size(), roads.size());
        }
    }

    private void projectGraph(Session session) {
        session.executeWrite(tx -> {
            tx.run("CALL gds.graph.drop('road-current', false) YIELD graphName RETURN graphName");
            return null;
        });
        try {
            session.executeWrite(tx -> {
                tx.run("""
                        MATCH (source:Intersection {version: $v})-[r:ROAD {version: $v}]->(target:Intersection {version: $v})
                        WITH gds.graph.project(
                          'road-current',
                          source,
                          target,
                          {
                            sourceNodeProperties: source { .lat, .lon },
                            targetNodeProperties: target { .lat, .lon },
                            relationshipProperties: r { .cost }
                          }
                        ) AS g
                        RETURN g.graphName
                        """, Values.parameters("v", VERSION));
                return null;
            });
        } catch (Exception ex) {
            log.warn("GDS Cypher 投影失败，尝试 project.cypher: {}", ex.getMessage());
            session.executeWrite(tx -> {
                tx.run("""
                        CALL gds.graph.project.cypher(
                          'road-current',
                          'MATCH (n:Intersection {version: %d}) RETURN id(n) AS id, n.lat AS lat, n.lon AS lon',
                          'MATCH (a:Intersection {version: %d})-[r:ROAD {version: %d}]->(b:Intersection {version: %d})
                           RETURN id(a) AS source, id(b) AS target, r.cost AS cost'
                        )
                        """.formatted(VERSION, VERSION, VERSION, VERSION));
                return null;
            });
        }
    }

    /**
     * 两端有坐标走 GDS A*（latitudeProperty/longitudeProperty）；失败或无启发式则 Dijkstra。
     * 不用 Cypher shortestPath（按跳数、不带权）。
     */
    public GridPathFinder.Path shortest(int srcId, int dstId, boolean astar) {
        if (astar) {
            try {
                return runGds(srcId, dstId, true);
            } catch (Exception ex) {
                log.warn("GDS A* 失败，启发式降级 Dijkstra: {}", ex.getMessage());
            }
        }
        return runGds(srcId, dstId, false);
    }

    private GridPathFinder.Path runGds(int srcId, int dstId, boolean astar) {
        String proc = astar ? "gds.shortestPath.astar.stream" : "gds.shortestPath.dijkstra.stream";
        String extra = astar ? ", latitudeProperty: 'lat', longitudeProperty: 'lon'" : "";
        try (Session session = driver.session()) {
            var result = session.run("""
                    MATCH (s:Intersection {id: $src, version: $v})
                    MATCH (t:Intersection {id: $dst, version: $v})
                    CALL %s('road-current', {
                      sourceNode: s,
                      targetNode: t,
                      relationshipWeightProperty: 'cost'%s
                    })
                    YIELD nodeIds, totalCost
                    RETURN [nodeId IN nodeIds | gds.util.asNode(nodeId).id] AS ids, totalCost
                    """.formatted(proc, extra),
                    Values.parameters("src", srcId, "dst", dstId, "v", VERSION));
            if (!result.hasNext()) {
                throw new IllegalStateException("GDS 无路径");
            }
            var rec = result.next();
            List<Integer> ids = rec.get("ids").asList(v -> v.asInt());
            return new GridPathFinder.Path(ids, rec.get("totalCost").asDouble(), astar ? "ASTAR" : "DIJKSTRA");
        }
    }

    public void updateCongestion(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        try (Session session = driver.session()) {
            for (List<Map<String, Object>> batch : batches(rows, BATCH)) {
                List<Map<String, Object>> chunk = new ArrayList<>(batch);
                session.executeWrite(tx -> {
                    tx.run("""
                            UNWIND $rows AS row
                            MATCH ()-[r:ROAD {roadId: row.roadId, version: $v}]->()
                            SET r.congestion = row.congestion, r.cost = r.baseTime * row.congestion
                            """, Values.parameters("rows", chunk, "v", VERSION));
                    return null;
                });
            }
            projectGraph(session);
            log.info("Neo4j GDS 投影已刷新（congestion 边权）");
        }
    }

    private static <T> List<List<T>> batches(List<T> all, int size) {
        List<List<T>> out = new ArrayList<>();
        for (int i = 0; i < all.size(); i += size) {
            out.add(all.subList(i, Math.min(i + size, all.size())));
        }
        return out;
    }
}

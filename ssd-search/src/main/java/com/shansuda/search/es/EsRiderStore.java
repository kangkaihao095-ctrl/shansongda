package com.shansuda.search.es;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.DistanceUnit;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.GetResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.shansuda.search.service.RiderDoc;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EsRiderStore {

    public static final String INDEX = "ssd_riders";
    private static final Logger log = LoggerFactory.getLogger(EsRiderStore.class);

    private final ElasticsearchClient client;

    public EsRiderStore(String url) {
        RestClient rest = RestClient.builder(HttpHost.create(url)).build();
        this.client = new ElasticsearchClient(new RestClientTransport(rest, new JacksonJsonpMapper()));
    }

    public boolean ping() {
        try {
            return client.ping().value();
        } catch (Exception ex) {
            log.warn("ES 不可用: {}", ex.getMessage());
            return false;
        }
    }

    public void ensureIndex() {
        try {
            boolean exists = client.indices().exists(e -> e.index(INDEX)).value();
            if (exists) {
                return;
            }
            client.indices().create(c -> c.index(INDEX).mappings(m -> m
                    .properties("riderId", p -> p.long_(l -> l))
                    .properties("location", p -> p.geoPoint(g -> g))
                    .properties("onlineStatus", p -> p.keyword(k -> k))
                    .properties("acceptStatus", p -> p.keyword(k -> k))
                    .properties("updateTime", p -> p.date(d -> d))
                    .properties("version", p -> p.long_(l -> l))
            ));
        } catch (Exception ex) {
            log.warn("创建索引失败: {}", ex.getMessage());
        }
    }

    public boolean upsert(RiderDoc doc) {
        try {
            GetResponse<Map> existing = client.get(g -> g.index(INDEX).id(String.valueOf(doc.riderId())), Map.class);
            if (existing.found() && existing.source() != null) {
                Object ver = existing.source().get("version");
                long current = ver instanceof Number n ? n.longValue() : 0;
                if (current > doc.version()) {
                    return false;
                }
            }
            Map<String, Object> source = new HashMap<>();
            source.put("riderId", doc.riderId());
            source.put("location", Map.of("lat", doc.lat(), "lon", doc.lon()));
            source.put("onlineStatus", doc.onlineStatus());
            source.put("acceptStatus", doc.acceptStatus());
            source.put("updateTime", doc.updateTime().toString());
            source.put("version", doc.version());
            client.index(i -> i.index(INDEX).id(String.valueOf(doc.riderId())).document(source));
            return true;
        } catch (Exception ex) {
            log.warn("ES 写入失败: {}", ex.getMessage());
            return false;
        }
    }

    public List<RiderDoc> nearby(double lat, double lon, int radiusMeters, String onlineStatus, String acceptStatus) {
        try {
            Query geo = Query.of(q -> q.geoDistance(g -> g
                    .field("location")
                    .distance(radiusMeters + "m")
                    .location(l -> l.latlon(ll -> ll.lat(lat).lon(lon)))));
            Query bool = Query.of(q -> q.bool(b -> {
                b.filter(geo);
                if (onlineStatus != null && !onlineStatus.isBlank()) {
                    b.filter(f -> f.term(t -> t.field("onlineStatus").value(onlineStatus)));
                }
                if (acceptStatus != null && !acceptStatus.isBlank()) {
                    b.filter(f -> f.term(t -> t.field("acceptStatus").value(acceptStatus)));
                }
                return b;
            }));
            SearchResponse<Map> resp = client.search(s -> s.index(INDEX).query(bool)
                    .sort(so -> so.geoDistance(g -> g.field("location")
                            .location(l -> l.latlon(ll -> ll.lat(lat).lon(lon)))
                            .order(SortOrder.Asc)
                            .unit(DistanceUnit.Meters))), Map.class);
            List<RiderDoc> docs = new ArrayList<>();
            resp.hits().hits().forEach(hit -> {
                Map src = hit.source();
                if (src == null) {
                    return;
                }
                docs.add(fromMap(src));
            });
            return docs;
        } catch (Exception ex) {
            log.warn("ES 检索失败: {}", ex.getMessage());
            return List.of();
        }
    }

    public int count() {
        try {
            return (int) client.count(c -> c.index(INDEX)).count();
        } catch (Exception ex) {
            return -1;
        }
    }

    @SuppressWarnings("unchecked")
    private static RiderDoc fromMap(Map src) {
        Map loc = src.get("location") instanceof Map m ? m : Map.of();
        return new RiderDoc(
                ((Number) src.get("riderId")).longValue(),
                ((Number) loc.getOrDefault("lat", 0)).doubleValue(),
                ((Number) loc.getOrDefault("lon", 0)).doubleValue(),
                String.valueOf(src.getOrDefault("onlineStatus", "")),
                String.valueOf(src.getOrDefault("acceptStatus", "")),
                Instant.parse(String.valueOf(src.getOrDefault("updateTime", Instant.now().toString()))),
                src.get("version") instanceof Number n ? n.longValue() : 0
        );
    }
}

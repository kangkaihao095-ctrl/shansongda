package com.shansuda.search.service;

import com.shansuda.search.canal.CanalRiderConsumer;
import com.shansuda.search.es.EsRiderStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SearchService {

    private static final Logger log = LoggerFactory.getLogger(SearchService.class);

    private final RiderIndex memory = new RiderIndex();
    private final JdbcTemplate jdbc;
    private final String mode;
    private final String esUrl;
    private final String canalHost;
    private final int canalPort;
    private EsRiderStore es;
    private boolean useEs;
    private CanalRiderConsumer canal;

    public SearchService(DataSource dataSource,
                         @Value("${ssd.mode:auto}") String mode,
                         @Value("${ssd.es.url:http://127.0.0.1:9210}") String esUrl,
                         @Value("${ssd.canal.host:127.0.0.1}") String canalHost,
                         @Value("${ssd.canal.port:11111}") int canalPort) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.mode = mode;
        this.esUrl = esUrl;
        this.canalHost = canalHost;
        this.canalPort = canalPort;
    }

    @PostConstruct
    public void start() {
        if (!"dry-run".equals(mode)) {
            try {
                es = new EsRiderStore(esUrl);
                if (es.ping()) {
                    es.ensureIndex();
                    useEs = true;
                }
            } catch (Exception ex) {
                log.warn("ES 初始化失败: {}", ex.getMessage());
            }
            canal = new CanalRiderConsumer(this, canalHost, canalPort);
            canal.start();
        }
        reconcile();
    }

    @PreDestroy
    public void stop() {
        if (canal != null) {
            canal.stop();
        }
    }

    public boolean apply(RiderDoc doc) {
        boolean accepted = memory.upsert(doc);
        if (!accepted) {
            return false;
        }
        if (useEs && es != null) {
            es.upsert(doc);
        }
        return true;
    }

    public List<Map<String, Object>> nearby(double lat, double lon, int radiusMeters,
                                            String onlineStatus, String acceptStatus) {
        List<RiderDoc> docs;
        if (useEs && es != null) {
            docs = es.nearby(lat, lon, radiusMeters, onlineStatus, acceptStatus);
            if (docs.isEmpty()) {
                docs = memory.nearby(lat, lon, radiusMeters, onlineStatus, acceptStatus);
            }
        } else {
            docs = memory.nearby(lat, lon, radiusMeters, onlineStatus, acceptStatus);
        }
        return docs.stream().map(this::view).toList();
    }

    @Scheduled(fixedDelayString = "${ssd.search.reconcile-ms:60000}")
    public void reconcile() {
        try {
            List<RiderDoc> rows = jdbc.query(
                    "SELECT user_id, lat, lon, online_status, accept_status, update_time, version FROM rider",
                    (rs, i) -> new RiderDoc(
                            rs.getLong("user_id"),
                            rs.getDouble("lat"),
                            rs.getDouble("lon"),
                            rs.getString("online_status"),
                            rs.getString("accept_status"),
                            toInstant(rs.getTimestamp("update_time")),
                            rs.getLong("version")
                    ));
            for (RiderDoc row : rows) {
                RiderDoc current = memory.get(row.riderId());
                if (current == null || !current.updateTime().equals(row.updateTime())
                        || current.version() != row.version()) {
                    apply(row);
                }
            }
        } catch (Exception ex) {
            log.warn("校准失败: {}", ex.getMessage());
        }
    }

    private Map<String, Object> view(RiderDoc doc) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("riderId", doc.riderId());
        map.put("lat", doc.lat());
        map.put("lon", doc.lon());
        map.put("onlineStatus", doc.onlineStatus());
        map.put("acceptStatus", doc.acceptStatus());
        map.put("updateTime", doc.updateTime());
        map.put("version", doc.version());
        map.put("distanceKm", null);
        return map;
    }

    private static Instant toInstant(Timestamp ts) {
        return ts == null ? Instant.now() : ts.toInstant();
    }
}

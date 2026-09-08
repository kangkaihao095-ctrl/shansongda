package com.shansuda.search.canal;

import com.alibaba.otter.canal.client.CanalConnector;
import com.alibaba.otter.canal.client.CanalConnectors;
import com.alibaba.otter.canal.protocol.CanalEntry;
import com.alibaba.otter.canal.protocol.Message;
import com.shansuda.search.service.RiderDoc;
import com.shansuda.search.service.SearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class CanalRiderConsumer implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(CanalRiderConsumer.class);
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final SearchService searchService;
    private final String host;
    private final int port;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private Thread worker;

    public CanalRiderConsumer(SearchService searchService, String host, int port) {
        this.searchService = searchService;
        this.host = host;
        this.port = port;
    }

    public void start() {
        worker = new Thread(this, "canal-rider");
        worker.setDaemon(true);
        worker.start();
    }

    public void stop() {
        running.set(false);
        if (worker != null) {
            worker.interrupt();
        }
    }

    @Override
    public void run() {
        long backoffMs = 1000;
        while (running.get()) {
            CanalConnector connector = CanalConnectors.newSingleConnector(
                    new InetSocketAddress(host, port), "example", "", "");
            try {
                connector.connect();
                connector.subscribe("ssd_account.rider");
                connector.rollback();
                log.info("Canal 已订阅 ssd_account.rider");
                backoffMs = 1000;
                while (running.get()) {
                    Message message = connector.getWithoutAck(100);
                    long batchId = message.getId();
                    if (batchId == -1 || message.getEntries().isEmpty()) {
                        Thread.sleep(200);
                        continue;
                    }
                    for (CanalEntry.Entry entry : message.getEntries()) {
                        if (entry.getEntryType() != CanalEntry.EntryType.ROWDATA) {
                            continue;
                        }
                        CanalEntry.RowChange change = CanalEntry.RowChange.parseFrom(entry.getStoreValue());
                        for (CanalEntry.RowData row : change.getRowDatasList()) {
                            apply(row.getAfterColumnsList());
                        }
                    }
                    connector.ack(batchId);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception ex) {
                log.warn("Canal 消费异常，将重连: {}", ex.getMessage());
            } finally {
                try {
                    connector.disconnect();
                } catch (Exception ignored) {
                }
            }
            if (!running.get()) {
                return;
            }
            try {
                Thread.sleep(backoffMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            backoffMs = Math.min(backoffMs * 2, 30_000);
        }
    }

    private void apply(List<CanalEntry.Column> columns) {
        Map<String, String> map = new HashMap<>();
        columns.forEach(c -> map.put(c.getName(), c.getValue()));
        if (!map.containsKey("user_id")) {
            return;
        }
        searchService.apply(new RiderDoc(
                Long.parseLong(map.get("user_id")),
                Double.parseDouble(map.getOrDefault("lat", "0")),
                Double.parseDouble(map.getOrDefault("lon", "0")),
                map.getOrDefault("online_status", "OFFLINE"),
                map.getOrDefault("accept_status", "IDLE"),
                parseTime(map.get("update_time")),
                Long.parseLong(map.getOrDefault("version", "1"))
        ));
    }

    private static Instant parseTime(String raw) {
        if (raw == null || raw.isBlank()) {
            return Instant.now();
        }
        try {
            return LocalDateTime.parse(raw, TS).atZone(ZoneId.systemDefault()).toInstant();
        } catch (Exception ex) {
            return Instant.now();
        }
    }
}

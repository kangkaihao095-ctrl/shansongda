package com.shansuda.search.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 版本更旧的变更丢弃；同时作为 ES 不可用时的内存索引。 */
public class RiderIndex {

    private final Map<Long, RiderDoc> docs = new ConcurrentHashMap<>();

    public boolean upsert(RiderDoc incoming) {
        RiderDoc existing = docs.get(incoming.riderId());
        if (existing != null && existing.version() > incoming.version()) {
            return false;
        }
        docs.put(incoming.riderId(), incoming);
        return true;
    }

    public RiderDoc get(long riderId) {
        return docs.get(riderId);
    }

    public List<RiderDoc> nearby(double lat, double lon, int radiusMeters, String onlineStatus, String acceptStatus) {
        List<RiderDoc> hits = new ArrayList<>();
        for (RiderDoc doc : docs.values()) {
            if (onlineStatus != null && !onlineStatus.equals(doc.onlineStatus())) {
                continue;
            }
            if (acceptStatus != null && !acceptStatus.equals(doc.acceptStatus())) {
                continue;
            }
            double km = haversineKm(lat, lon, doc.lat(), doc.lon());
            if (radiusMeters <= 0 || km * 1000 <= radiusMeters) {
                hits.add(doc);
            }
        }
        hits.sort(Comparator.comparingDouble(d -> haversineKm(lat, lon, d.lat(), d.lon())));
        return hits;
    }

    public List<RiderDoc> all() {
        return new ArrayList<>(docs.values());
    }

    public int size() {
        return docs.size();
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
}

package com.shansuda.account.lbs;

import com.shansuda.account.domain.Rider;
import com.shansuda.common.geo.Geo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 本机无 ES 时的 MySQL 距离兜底：Haversine 过滤 radius 米并按距离排序。
 */
public final class RiderNearby {

    private RiderNearby() {
    }

    public static List<Map<String, Object>> filterAndSort(List<Rider> riders, double lat, double lon,
                                                          int radiusMeters, String onlineStatus, String acceptStatus) {
        int radius = radiusMeters <= 0 ? Integer.MAX_VALUE : radiusMeters;
        List<Hit> hits = new ArrayList<>();
        for (Rider rider : riders) {
            if (rider.getLat() == null || rider.getLon() == null) {
                continue;
            }
            if (onlineStatus != null && !onlineStatus.isBlank() && !onlineStatus.equals(rider.getOnlineStatus())) {
                continue;
            }
            if (acceptStatus != null && !acceptStatus.isBlank() && !acceptStatus.equals(rider.getAcceptStatus())) {
                continue;
            }
            double km = Geo.haversineKm(lat, lon, rider.getLat(), rider.getLon());
            if (!Double.isFinite(km) || km * 1000 > radius) {
                continue;
            }
            hits.add(new Hit(rider, km));
        }
        hits.sort(Comparator.comparingDouble(Hit::km));
        List<Map<String, Object>> rows = new ArrayList<>(hits.size());
        for (Hit hit : hits) {
            Rider rider = hit.rider;
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("riderId", rider.getUserId());
            body.put("userId", rider.getUserId());
            body.put("lat", rider.getLat());
            body.put("lon", rider.getLon());
            body.put("onlineStatus", rider.getOnlineStatus());
            body.put("acceptStatus", rider.getAcceptStatus());
            body.put("updateTime", rider.getUpdateTime());
            body.put("version", rider.getVersion());
            body.put("distanceKm", Geo.roundKm(hit.km));
            body.put("distanceMeters", Math.round(hit.km * 1000));
            rows.add(body);
        }
        return rows;
    }

    private record Hit(Rider rider, double km) {
    }
}

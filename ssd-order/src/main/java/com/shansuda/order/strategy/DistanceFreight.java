package com.shansuda.order.strategy;

import java.time.Instant;

public class DistanceFreight implements FreightStrategy {

    @Override
    public String name() {
        return "DistanceFreight";
    }

    @Override
    public int quote(double merchantLat, double merchantLon, double userLat, double userLon, Instant now) {
        double km = Geo.haversineKm(merchantLat, merchantLon, userLat, userLon);
        if (km <= 2) {
            return 300;
        }
        if (km <= 5) {
            return 600;
        }
        return 600 + (int) Math.ceil(km - 5) * 150;
    }
}

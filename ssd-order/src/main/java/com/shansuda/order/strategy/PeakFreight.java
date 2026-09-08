package com.shansuda.order.strategy;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public class PeakFreight implements FreightStrategy {

    private final DistanceFreight distance = new DistanceFreight();

    @Override
    public String name() {
        return "PeakFreight";
    }

    @Override
    public int quote(double merchantLat, double merchantLon, double userLat, double userLon, Instant now) {
        int base = distance.quote(merchantLat, merchantLon, userLat, userLon, now);
        return (int) Math.round(base * 1.2);
    }

    public static boolean peakNow(Instant now) {
        int hour = ZonedDateTime.ofInstant(now, ZoneId.of("Asia/Shanghai")).getHour();
        return (hour >= 11 && hour < 13) || (hour >= 17 && hour < 20);
    }
}

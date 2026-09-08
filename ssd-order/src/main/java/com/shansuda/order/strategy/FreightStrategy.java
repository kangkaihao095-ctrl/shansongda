package com.shansuda.order.strategy;

import java.time.Instant;

public interface FreightStrategy {
    String name();

    int quote(double merchantLat, double merchantLon, double userLat, double userLon, Instant now);
}

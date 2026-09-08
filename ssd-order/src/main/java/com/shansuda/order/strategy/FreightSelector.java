package com.shansuda.order.strategy;

import java.time.Instant;
import java.util.List;

public class FreightSelector {

    private final DistanceFreight distance = new DistanceFreight();
    private final PeakFreight peak = new PeakFreight();

    public FreightStrategy select(Instant now) {
        return PeakFreight.peakNow(now) ? peak : distance;
    }

    public List<FreightStrategy> all() {
        return List.of(distance, peak);
    }
}

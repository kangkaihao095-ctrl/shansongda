package com.shansuda.order.strategy;

import java.time.Instant;
import java.util.List;

public class FreightSelector {

    private final DistanceFreight distance = new DistanceFreight();
    private final PeakFreight peak = new PeakFreight();

    public FreightStrategy select(Instant now) {
        return select(now, MemberFreight.Context.none());
    }

    public FreightStrategy select(Instant now, MemberFreight.Context member) {
        FreightStrategy base = PeakFreight.peakNow(now) ? peak : distance;
        return MemberFreight.wrap(base, member);
    }

    public List<FreightStrategy> all() {
        return List.of(distance, peak);
    }
}

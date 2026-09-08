package com.shansuda.search.service;

import java.time.Instant;

public record RiderDoc(
        long riderId,
        double lat,
        double lon,
        String onlineStatus,
        String acceptStatus,
        Instant updateTime,
        long version
) {
}

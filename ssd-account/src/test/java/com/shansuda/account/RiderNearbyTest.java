package com.shansuda.account;

import com.shansuda.account.domain.Rider;
import com.shansuda.account.lbs.RiderNearby;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RiderNearbyTest {

    @Test
    void filtersOnlineIdleAndSortsByDistance() {
        Rider near = rider(1, 31.230, 121.470, "ONLINE", "IDLE");
        Rider far = rider(2, 31.250, 121.490, "ONLINE", "IDLE");
        Rider busy = rider(3, 31.230, 121.470, "ONLINE", "BUSY");
        Rider offline = rider(4, 31.230, 121.470, "OFFLINE", "IDLE");
        List<Map<String, Object>> hits = RiderNearby.filterAndSort(
                List.of(far, near, busy, offline), 31.230, 121.470, 5000, "ONLINE", "IDLE");
        assertEquals(2, hits.size());
        assertEquals(1L, hits.get(0).get("riderId"));
        assertEquals(2L, hits.get(1).get("riderId"));
        assertTrue(((Number) hits.get(0).get("distanceMeters")).doubleValue()
                <= ((Number) hits.get(1).get("distanceMeters")).doubleValue());
    }

    @Test
    void boundingBoxDropsOutsideBeforeHaversine() {
        double[] box = com.shansuda.common.geo.Geo.boundingBox(31.230, 121.470, 800);
        assertTrue(com.shansuda.common.geo.Geo.inBoundingBox(31.230, 121.470, box));
        assertTrue(!com.shansuda.common.geo.Geo.inBoundingBox(31.280, 121.540, box));
        Rider near = rider(1, 31.230, 121.470, "ONLINE", "IDLE");
        Rider far = rider(2, 31.280, 121.540, "ONLINE", "IDLE");
        List<Map<String, Object>> hits = RiderNearby.filterAndSort(
                List.of(near, far), 31.230, 121.470, 800, "ONLINE", "IDLE");
        assertEquals(1, hits.size());
        assertEquals(1L, hits.get(0).get("riderId"));
    }

    @Test
    void radiusCutsFarRiders() {
        Rider near = rider(1, 31.230, 121.470, "ONLINE", "IDLE");
        Rider far = rider(2, 31.280, 121.540, "ONLINE", "IDLE");
        List<Map<String, Object>> hits = RiderNearby.filterAndSort(
                List.of(near, far), 31.230, 121.470, 800, "ONLINE", "IDLE");
        assertEquals(1, hits.size());
        assertEquals(1L, hits.get(0).get("riderId"));
    }

    @Test
    void anyStatusKeepsOfflineAndBusy() {
        Rider near = rider(1, 31.230, 121.470, "ONLINE", "IDLE");
        Rider busy = rider(3, 31.231, 121.471, "ONLINE", "BUSY");
        Rider offline = rider(4, 31.229, 121.469, "OFFLINE", "IDLE");
        List<Map<String, Object>> hits = RiderNearby.filterAndSort(
                List.of(near, busy, offline), 31.230, 121.470, 5000, null, null);
        assertEquals(3, hits.size());
        assertEquals(1L, hits.get(0).get("riderId"));
    }

    private static Rider rider(long id, double lat, double lon, String online, String accept) {
        Rider rider = new Rider();
        rider.setUserId(id);
        rider.setLat(lat);
        rider.setLon(lon);
        rider.setOnlineStatus(online);
        rider.setAcceptStatus(accept);
        rider.setVersion(1L);
        return rider;
    }
}

package com.shansuda.order.route;

import com.shansuda.common.route.GridPathFinder;

/**
 * 顺路近似：相对进行中单用户点的夹角或绕路增量，不是 TSP。
 */
public final class AlongWay {

    public static final double ANGLE_DEG = 50;
    public static final double EXTRA_KM = 1.2;

    private AlongWay() {
    }

    public static boolean along(double riderLat, double riderLon,
                                double currentUserLat, double currentUserLon,
                                double merchantLat, double merchantLon,
                                double grabUserLat, double grabUserLon) {
        double angle = Math.abs(normalizeDeg(
                bearing(riderLat, riderLon, currentUserLat, currentUserLon)
                        - bearing(riderLat, riderLon, merchantLat, merchantLon)));
        if (angle > 180) {
            angle = 360 - angle;
        }
        double direct = GridPathFinder.haversineKm(riderLat, riderLon, currentUserLat, currentUserLon);
        double via = GridPathFinder.haversineKm(riderLat, riderLon, merchantLat, merchantLon)
                + GridPathFinder.haversineKm(merchantLat, merchantLon, grabUserLat, grabUserLon)
                + GridPathFinder.haversineKm(grabUserLat, grabUserLon, currentUserLat, currentUserLon);
        double extra = via - direct;
        return angle <= ANGLE_DEG || extra <= EXTRA_KM;
    }

    static double bearing(double lat1, double lon1, double lat2, double lon2) {
        double p1 = Math.toRadians(lat1);
        double p2 = Math.toRadians(lat2);
        double dLon = Math.toRadians(lon2 - lon1);
        double y = Math.sin(dLon) * Math.cos(p2);
        double x = Math.cos(p1) * Math.sin(p2) - Math.sin(p1) * Math.cos(p2) * Math.cos(dLon);
        return Math.toDegrees(Math.atan2(y, x));
    }

    static double normalizeDeg(double deg) {
        double v = deg % 360;
        if (v > 180) {
            v -= 360;
        }
        if (v < -180) {
            v += 360;
        }
        return v;
    }
}

package com.shansuda.common.geo;

/** 球面距离。配送半径与推荐打分共用，避免 account / order 各写一套。 */
public final class Geo {

    public static final double DEFAULT_MAX_KM = 5.0;

    private Geo() {
    }

    public static double haversineKm(double lat1, double lon1, Double lat2, Double lon2) {
        if (lat2 == null || lon2 == null) {
            return Double.POSITIVE_INFINITY;
        }
        double r = 6371.0;
        double p1 = Math.toRadians(lat1);
        double p2 = Math.toRadians(lat2);
        double dp = Math.toRadians(lat2 - lat1);
        double dl = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dp / 2) * Math.sin(dp / 2)
                + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) * Math.sin(dl / 2);
        return 2 * r * Math.asin(Math.min(1.0, Math.sqrt(a)));
    }

    public static boolean inRange(double userLat, double userLon, Double shopLat, Double shopLon, double maxKm) {
        return haversineKm(userLat, userLon, shopLat, shopLon) <= maxKm;
    }

    public static double roundKm(double km) {
        if (!Double.isFinite(km)) {
            return -1;
        }
        return Math.round(km * 10.0) / 10.0;
    }
}

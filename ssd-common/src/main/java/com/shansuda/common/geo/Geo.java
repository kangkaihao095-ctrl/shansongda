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

    /**
     * 半径对应的 lat/lon 包围盒：{@code [minLat, maxLat, minLon, maxLon]}。
     * 先用包围盒缩小候选，再用 Haversine 精滤。
     */
    public static double[] boundingBox(double lat, double lon, int radiusMeters) {
        double meters = radiusMeters <= 0 ? 0 : radiusMeters;
        double dLat = meters / 110_540.0;
        double cos = Math.cos(Math.toRadians(lat));
        double dLon = meters / (111_320.0 * Math.max(0.2, Math.abs(cos)));
        return new double[]{lat - dLat, lat + dLat, lon - dLon, lon + dLon};
    }

    public static boolean inBoundingBox(double lat, double lon, double[] box) {
        if (box == null || box.length < 4) {
            return true;
        }
        return lat >= box[0] && lat <= box[1] && lon >= box[2] && lon <= box[3];
    }
}

package com.shansuda.account.catalog;

/**
 * 首页「为你推荐」可解释加权。不引入 Spark。
 * <p>
 * 有历史完成单：
 * {@code 0.35 * 距离 + 0.25 * 品类亲和 + 0.20 * 销量 + 0.15 * 评分 + 0.05 * 在线}
 * <p>
 * 未登录/无完成单历史则把品类项摊到距离与销量：
 * {@code 0.45 * 距离 + 0.30 * 销量 + 0.20 * 评分 + 0.05 * 在线}
 * <p>
 * 距离：用户默认地址 vs 商家，Haversine，0km=1，≥8km=0。<br>
 * 品类亲和：该品类在用户 COMPLETED 订单中的频次 / 最大频次。<br>
 * 销量：log1p(完成单) / log1p(全局最大完成单)。<br>
 * 评分：ratingAvg / 5。<br>
 * 在线：ONLINE=1，打烊=0.28（仍展示，排序靠后）。
 */
public final class RecommendScorer {

    public static final double W_DISTANCE = 0.35;
    public static final double W_AFFINITY = 0.25;
    public static final double W_SALES = 0.20;
    public static final double W_RATING = 0.15;
    public static final double W_ONLINE = 0.05;
    public static final double MAX_KM = 8.0;

    private RecommendScorer() {
    }

    public static double score(double userLat, double userLon, Double shopLat, Double shopLon,
                               int categoryFreq, int maxCategoryFreq,
                               int completedCount, int maxCompleted,
                               Double ratingAvg, boolean online, boolean hasHistory) {
        double dist = distanceScore(userLat, userLon, shopLat, shopLon);
        double aff = maxCategoryFreq <= 0 ? 0 : Math.min(1.0, categoryFreq / (double) maxCategoryFreq);
        double sales = maxCompleted <= 0 ? 0 : Math.log1p(Math.max(0, completedCount)) / Math.log1p(maxCompleted);
        double rating = ratingScore(ratingAvg);
        double on = online ? 1.0 : 0.28;
        if (!hasHistory) {
            return 0.45 * dist + 0.30 * sales + 0.20 * rating + 0.05 * on;
        }
        return W_DISTANCE * dist + W_AFFINITY * aff + W_SALES * sales + W_RATING * rating + W_ONLINE * on;
    }

    /**
     * 评分项：均分线性映射后再加大差评惩罚（&lt;4.0 额外 ×0.65，并平方压低）。
     */
    public static double ratingScore(Double ratingAvg) {
        double avg = ratingAvg == null ? 4.6 : Math.max(0, Math.min(5.0, ratingAvg));
        double linear = avg / 5.0;
        if (avg < 4.0) {
            linear *= 0.65;
        }
        return linear * linear;
    }

    public static double distanceScore(double userLat, double userLon, Double shopLat, Double shopLon) {
        if (shopLat == null || shopLon == null) {
            return 0.4;
        }
        double km = haversineKm(userLat, userLon, shopLat, shopLon);
        if (km <= 0) {
            return 1.0;
        }
        if (km >= MAX_KM) {
            return 0.0;
        }
        return 1.0 - km / MAX_KM;
    }

    public static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        return com.shansuda.common.geo.Geo.haversineKm(lat1, lon1, lat2, lon2);
    }
}

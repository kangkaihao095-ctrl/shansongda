package com.shansuda.order.strategy;

/**
 * 派单 / 大厅路径代价排序：cost 升序；接近时骑手信用（准时率、评分）高者优先。
 */
public final class DispatchRank {

    /** 相对差 8% 或绝对差 0.25（约 15 秒）视为接近。 */
    public static final double RELATIVE_CLOSE = 0.08;
    public static final double ABS_CLOSE = 0.25;

    private DispatchRank() {
    }

    public static boolean close(double costA, double costB) {
        double gap = Math.abs(costA - costB);
        double scale = Math.max(costA, costB);
        return gap <= ABS_CLOSE || (scale > 0 && gap / scale <= RELATIVE_CLOSE);
    }

    public static double credit(Double onTimeRate, Double ratingAvg) {
        double onTime = onTimeRate == null ? 0.9 : Math.max(0, Math.min(1.0, onTimeRate));
        double rating = ratingAvg == null ? 4.6 : Math.max(0, Math.min(5.0, ratingAvg));
        return onTime * 0.6 + (rating / 5.0) * 0.4;
    }

    public static int compare(double costA, Double onTimeA, Double ratingA,
                              double costB, Double onTimeB, Double ratingB) {
        if (!close(costA, costB)) {
            return Double.compare(costA, costB);
        }
        int creditCmp = Double.compare(credit(onTimeB, ratingB), credit(onTimeA, ratingA));
        if (creditCmp != 0) {
            return creditCmp;
        }
        return Double.compare(costA, costB);
    }
}

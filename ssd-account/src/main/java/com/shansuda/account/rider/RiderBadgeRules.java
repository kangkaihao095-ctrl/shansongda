package com.shansuda.account.rider;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 勋章按完成单数 / 评分 / 准时率 / 打赏阈值授予，不写死无意义图标。 */
public final class RiderBadgeRules {

    public static final int HUNDRED_ORDERS = 100;
    public static final double PRAISE_RATING = 4.8;
    public static final int PRAISE_REVIEWS = 20;
    public static final double ON_TIME_MIN = 0.95;
    public static final int TIP_STAR_CENTS = 2000;

    private RiderBadgeRules() {
    }

    public static List<Map<String, Object>> badges(int completedCount, double ratingAvg, int ratingCount,
                                                   double onTimeRate, int tipCentsTotal) {
        List<Map<String, Object>> list = new ArrayList<>();
        list.add(badge("HUNDRED", "百单达人", completedCount >= HUNDRED_ORDERS,
                "完成配送满 " + HUNDRED_ORDERS + " 单"));
        list.add(badge("PRAISE", "好评如潮",
                ratingAvg >= PRAISE_RATING && ratingCount >= PRAISE_REVIEWS,
                "评分 ≥ " + PRAISE_RATING + " 且评价满 " + PRAISE_REVIEWS + " 条"));
        list.add(badge("ON_TIME", "准点骑士", onTimeRate >= ON_TIME_MIN,
                "准时率 ≥ " + Math.round(ON_TIME_MIN * 100) + "%（档案口径）"));
        list.add(badge("TIP_STAR", "打赏之星", tipCentsTotal >= TIP_STAR_CENTS,
                "累计收到打赏满 " + (TIP_STAR_CENTS / 100) + " 元"));
        return list;
    }

    private static Map<String, Object> badge(String code, String name, boolean earned, String hint) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("code", code);
        row.put("name", name);
        row.put("earned", earned);
        row.put("hint", hint);
        return row;
    }
}

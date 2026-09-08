package com.shansuda.account.catalog;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 闪送达会员等级：开通即 1 级，按净实付（分）成长。年费仅影响「年」标，不改变等级阈值。
 */
public final class MemberRules {

    public static final int MONTH_DAYS = 31;
    public static final int QUARTER_DAYS = 93;
    public static final int YEAR_DAYS = 365;
    public static final Duration MONTH = Duration.ofDays(MONTH_DAYS);
    public static final Duration QUARTER = Duration.ofDays(QUARTER_DAYS);
    public static final Duration YEAR = Duration.ofDays(YEAR_DAYS);

    public static final int MONTH_CENTS = 1500;
    public static final int QUARTER_CENTS = 4000;
    public static final int YEAR_CENTS = 12800;
    public static final int AUTO_MONTH_CENTS = 1200;

    /** 累计净实付达到该元数即进入对应等级（下标 0 = 1 级）。 */
    public static final int[] THRESHOLD_YUAN = {0, 200, 800, 2000, 5000, 12000, 30000};
    public static final String[] TITLES = {"体验会员", "银卡", "金卡", "白金", "钻石", "黑金", "王者"};

    private MemberRules() {
    }

    public static int level(int paidCentsNet) {
        int yuan = Math.max(0, paidCentsNet) / 100;
        int level = 1;
        for (int i = 0; i < THRESHOLD_YUAN.length; i++) {
            if (yuan >= THRESHOLD_YUAN[i]) {
                level = i + 1;
            }
        }
        return level;
    }

    public static String title(int level) {
        int idx = Math.max(1, Math.min(7, level)) - 1;
        return TITLES[idx];
    }

    /** 下一档门槛（元）；已满 7 级返回 -1。 */
    public static int nextThresholdYuan(int level) {
        if (level >= 7) {
            return -1;
        }
        return THRESHOLD_YUAN[Math.max(1, level)];
    }

    public static int thresholdYuan(int level) {
        int idx = Math.max(1, Math.min(7, level)) - 1;
        return THRESHOLD_YUAN[idx];
    }

    public static boolean validPlan(String plan) {
        return "MONTH".equals(plan) || "QUARTER".equals(plan)
                || "YEAR".equals(plan) || "AUTO_MONTH".equals(plan);
    }

    public static int days(String plan) {
        if ("QUARTER".equals(plan)) {
            return QUARTER_DAYS;
        }
        if ("YEAR".equals(plan)) {
            return YEAR_DAYS;
        }
        return MONTH_DAYS;
    }

    public static Duration duration(String plan) {
        return Duration.ofDays(days(plan));
    }

    public static int priceCents(String plan) {
        if ("QUARTER".equals(plan)) {
            return QUARTER_CENTS;
        }
        if ("YEAR".equals(plan)) {
            return YEAR_CENTS;
        }
        if ("AUTO_MONTH".equals(plan)) {
            return AUTO_MONTH_CENTS;
        }
        return MONTH_CENTS;
    }

    public static String planLabel(String plan) {
        if ("QUARTER".equals(plan)) {
            return "季卡";
        }
        if ("YEAR".equals(plan)) {
            return "年卡";
        }
        if ("AUTO_MONTH".equals(plan)) {
            return "连续包月";
        }
        if ("MONTH".equals(plan)) {
            return "月卡";
        }
        return "闪会员";
    }

    public static List<Map<String, Object>> catalog() {
        return List.of(
                planRow("MONTH", "月卡", MONTH_DAYS, MONTH_CENTS, null),
                planRow("QUARTER", "季卡", QUARTER_DAYS, QUARTER_CENTS, null),
                planRow("YEAR", "年卡", YEAR_DAYS, YEAR_CENTS, "年"),
                planRow("AUTO_MONTH", "连续包月", MONTH_DAYS, AUTO_MONTH_CENTS, "连续")
        );
    }

    private static Map<String, Object> planRow(String plan, String name, int days, int cents, String badge) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("plan", plan);
        row.put("name", name);
        row.put("days", days);
        row.put("cents", cents);
        row.put("badge", badge);
        return row;
    }
}

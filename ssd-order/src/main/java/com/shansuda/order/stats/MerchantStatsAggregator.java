package com.shansuda.order.stats;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 商家销量聚合：分片 scatter 后在内存按日/月归并，不写 SLA。 */
public final class MerchantStatsAggregator {

    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    public record OrderRow(String status, int goodsCents, int freightCents, Instant createdAt, int payCents) {
        public OrderRow(String status, int goodsCents, int freightCents, Instant createdAt) {
            this(status, goodsCents, freightCents, createdAt, goodsCents + freightCents);
        }
    }

    public record DayPoint(String date, int orderCount, long gmvCents, int completedCount, long refundCents) {
        public DayPoint(String date, int orderCount, long gmvCents, int completedCount) {
            this(date, orderCount, gmvCents, completedCount, 0);
        }
    }

    public record Stats(int todayOrders, long todayGmvCents, int inProgress, int completed,
                        long refundCents, String grain, String range, List<DayPoint> series) {
        public Map<String, Object> toMap() {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("todayOrders", todayOrders);
            body.put("todayGmvCents", todayGmvCents);
            body.put("inProgress", inProgress);
            body.put("completed", completed);
            body.put("refundCents", refundCents);
            body.put("grain", grain);
            body.put("range", range);
            body.put("series", series.stream().map(p -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("date", p.date());
                row.put("orderCount", p.orderCount());
                row.put("gmvCents", p.gmvCents());
                row.put("completedCount", p.completedCount());
                row.put("refundCents", p.refundCents());
                return row;
            }).toList());
            return body;
        }
    }

    private MerchantStatsAggregator() {
    }

    public static int resolveDays(String range, Integer days) {
        if (range != null && !range.isBlank()) {
            return switch (range.trim().toLowerCase()) {
                case "30d", "30" -> 30;
                case "1y", "365d", "365", "year" -> 365;
                default -> 7;
            };
        }
        if (days == null) {
            return 7;
        }
        if (days >= 180) {
            return 365;
        }
        if (days >= 20) {
            return 30;
        }
        return 7;
    }

    public static String rangeLabel(int days) {
        if (days >= 180) {
            return "1y";
        }
        if (days >= 20) {
            return "30d";
        }
        return "7d";
    }

    public static Stats aggregate(List<OrderRow> rows, LocalDate today, int days) {
        int window = days >= 180 ? 365 : (days >= 20 ? 30 : Math.min(Math.max(days, 1), 31));
        boolean monthly = window >= 180;
        LocalDate start = monthly ? today.minusMonths(11).withDayOfMonth(1) : today.minusDays(window - 1L);
        Map<String, DayPoint> buckets = new LinkedHashMap<>();
        if (monthly) {
            YearMonth cursor = YearMonth.from(start);
            YearMonth end = YearMonth.from(today);
            while (!cursor.isAfter(end)) {
                String key = cursor.toString();
                buckets.put(key, new DayPoint(key, 0, 0, 0, 0));
                cursor = cursor.plusMonths(1);
            }
        } else {
            for (int i = 0; i < window; i++) {
                LocalDate day = start.plusDays(i);
                buckets.put(day.toString(), new DayPoint(day.toString(), 0, 0, 0, 0));
            }
        }
        int todayOrders = 0;
        long todayGmv = 0;
        int inProgress = 0;
        int completed = 0;
        long refundCents = 0;
        for (OrderRow row : rows) {
            if (row.createdAt() == null) {
                continue;
            }
            LocalDate day = LocalDate.ofInstant(row.createdAt(), ZONE);
            boolean done = "COMPLETED".equals(row.status());
            boolean live = isLive(row.status());
            boolean refund = "REFUNDING".equals(row.status()) || "REFUNDED".equals(row.status());
            long gmv = (long) row.goodsCents() + row.freightCents();
            int pay = row.payCents() > 0 ? row.payCents() : (int) gmv;
            if (live) {
                inProgress++;
            }
            if (day.equals(today)) {
                todayOrders++;
                todayGmv += gmv;
                if (done) {
                    completed++;
                }
                if (refund) {
                    refundCents += pay;
                }
            }
            if (day.isBefore(start) || day.isAfter(today)) {
                continue;
            }
            String key = monthly ? YearMonth.from(day).toString() : day.toString();
            DayPoint prev = buckets.getOrDefault(key, new DayPoint(key, 0, 0, 0, 0));
            buckets.put(key, new DayPoint(key, prev.orderCount() + 1,
                    prev.gmvCents() + gmv, prev.completedCount() + (done ? 1 : 0),
                    prev.refundCents() + (refund ? pay : 0)));
        }
        return new Stats(todayOrders, todayGmv, inProgress, completed, refundCents,
                monthly ? "month" : "day", rangeLabel(window), new ArrayList<>(buckets.values()));
    }

    static boolean isLive(String status) {
        return "MERCHANT_PENDING".equals(status) || "PAID".equals(status)
                || "ACCEPTED".equals(status) || "ARRIVED".equals(status)
                || "DELIVERING".equals(status) || "REFUNDING".equals(status);
    }
}

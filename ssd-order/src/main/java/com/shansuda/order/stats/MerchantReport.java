package com.shansuda.order.stats;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 商家报表：摘要文案与 CSV，只复用已有聚合，不写 SLA。 */
public final class MerchantReport {

    public record SkuHit(String name, int qty) {
    }

    private MerchantReport() {
    }

    public static String rangeTitle(String range) {
        if (range == null) {
            return "近七日";
        }
        return switch (range.trim().toLowerCase()) {
            case "30d", "30" -> "近一月";
            case "1y", "365d", "365", "year" -> "近一年";
            default -> "近七日";
        };
    }

    public static String summary(MerchantStatsAggregator.Stats stats, List<SkuHit> top) {
        String title = rangeTitle(stats.range());
        int orders = 0;
        long gmv = 0;
        int completed = 0;
        long refund = 0;
        String peakDate = "";
        int peakOrders = 0;
        for (MerchantStatsAggregator.DayPoint p : stats.series()) {
            orders += p.orderCount();
            gmv += p.gmvCents();
            completed += p.completedCount();
            refund += p.refundCents();
            if (p.orderCount() > peakOrders) {
                peakOrders = p.orderCount();
                peakDate = p.date();
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append(title).append("共 ").append(orders).append(" 单，成交 ")
                .append(yuan(gmv)).append("，完成 ").append(completed)
                .append(" 单，退款 ").append(yuan(refund)).append("。");
        if (orders > 0) {
            sb.append("客单价 ").append(yuan(Math.round((double) gmv / orders))).append("。");
        }
        if (gmv > 0) {
            sb.append("退款率 ").append(String.format("%.1f", refund * 100.0 / gmv)).append("%。");
        }
        if (top == null || top.isEmpty()) {
            sb.append("暂无热销商品。");
        } else {
            sb.append("热销 Top").append(Math.min(3, top.size())).append("：");
            for (int i = 0; i < Math.min(3, top.size()); i++) {
                if (i > 0) {
                    sb.append("、");
                }
                SkuHit hit = top.get(i);
                sb.append(hit.name()).append("×").append(hit.qty());
            }
            sb.append("。");
        }
        if (peakOrders <= 0 || peakDate.isBlank()) {
            sb.append("高峰日尚未形成。");
        } else {
            sb.append("高峰日 ").append(peakDate).append("（").append(peakOrders).append(" 单）。");
        }
        return sb.toString();
    }

    public static byte[] csv(MerchantStatsAggregator.Stats stats) {
        StringBuilder sb = new StringBuilder();
        sb.append('\uFEFF');
        sb.append("日期,单量,GMV,完成,退款\n");
        for (MerchantStatsAggregator.DayPoint p : stats.series()) {
            sb.append(csvCell(p.date())).append(',')
                    .append(p.orderCount()).append(',')
                    .append(yuanPlain(p.gmvCents())).append(',')
                    .append(p.completedCount()).append(',')
                    .append(yuanPlain(p.refundCents()))
                    .append('\n');
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    public static List<SkuHit> topSkus(List<Map<String, Object>> completedSnapshots, int limit) {
        Map<String, Integer> qty = new LinkedHashMap<>();
        if (completedSnapshots != null) {
            for (Map<String, Object> snap : completedSnapshots) {
                Object items = snap == null ? null : snap.get("items");
                if (!(items instanceof List<?> list)) {
                    continue;
                }
                for (Object raw : list) {
                    if (!(raw instanceof Map<?, ?> row)) {
                        continue;
                    }
                    String name = row.get("name") == null ? "商品" : String.valueOf(row.get("name"));
                    int n = row.get("qty") instanceof Number q ? Math.max(1, q.intValue()) : 1;
                    qty.merge(name, n, Integer::sum);
                }
            }
        }
        List<SkuHit> hits = new ArrayList<>();
        qty.forEach((name, n) -> hits.add(new SkuHit(name, n)));
        hits.sort(Comparator.comparingInt(SkuHit::qty).reversed().thenComparing(SkuHit::name));
        if (hits.size() > limit) {
            return new ArrayList<>(hits.subList(0, limit));
        }
        return hits;
    }

    public static Map<String, Object> extras(MerchantStatsAggregator.Stats stats, List<SkuHit> top) {
        int orders = 0;
        long gmv = 0;
        int completed = 0;
        long refund = 0;
        String peakDate = "";
        int peakOrders = 0;
        for (MerchantStatsAggregator.DayPoint p : stats.series()) {
            orders += p.orderCount();
            gmv += p.gmvCents();
            completed += p.completedCount();
            refund += p.refundCents();
            if (p.orderCount() > peakOrders) {
                peakOrders = p.orderCount();
                peakDate = p.date();
            }
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("windowOrders", orders);
        body.put("windowGmvCents", gmv);
        body.put("windowCompleted", completed);
        body.put("windowRefundCents", refund);
        body.put("peakDate", peakDate);
        body.put("peakOrders", peakOrders);
        body.put("topSkus", top.stream().map(h -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", h.name());
            row.put("qty", h.qty());
            return row;
        }).toList());
        body.put("summary", summary(stats, top));
        long aov = orders <= 0 ? 0 : Math.round((double) gmv / orders);
        double refundRate = gmv <= 0 ? 0 : (refund * 1.0 / gmv);
        body.put("avgPayCents", aov);
        body.put("refundRate", Math.round(refundRate * 10000) / 10000.0);
        return body;
    }

    private static String yuan(long cents) {
        return "¥" + yuanPlain(cents);
    }

    private static String yuanPlain(long cents) {
        return String.format("%.2f", cents / 100.0);
    }

    private static String csvCell(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}

package com.shansuda.order.query;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 订单列表 size 约束与智能搜索（分片 scatter 后内存过滤）。 */
public final class OrderSearch {

    public static final int DEFAULT_SIZE = 10;
    public static final List<Integer> SIZES = List.of(5, 10, 20);
    /** q 关键词 scatter 扫描上限（每分片），避免全表扫。 */
    public static final int Q_SCAN_LIMIT = 500;

    public static final Map<String, String> STATUS_CN = Map.ofEntries(
            Map.entry("CREATED", "待支付"),
            Map.entry("MERCHANT_PENDING", "待商家接单"),
            Map.entry("PAID", "商家已接单·备餐中"),
            Map.entry("ACCEPTED", "骑手已接单"),
            Map.entry("ARRIVED", "骑手到店"),
            Map.entry("DELIVERING", "配送中"),
            Map.entry("COMPLETED", "已完成"),
            Map.entry("CANCELLING", "取消中"),
            Map.entry("CANCELLED", "已取消"),
            Map.entry("REFUNDING", "退款中"),
            Map.entry("REFUNDED", "退款成功"),
            Map.entry("REFUND_REJECTED", "退款拒绝")
    );

    private static final Map<String, List<String>> STATUS_ALIASES = Map.of(
            "PAID", List.of("待骑手接单", "待接单", "备餐中", "商家已接单"),
            "MERCHANT_PENDING", List.of("待接单"),
            "COMPLETED", List.of("已送达"),
            "ACCEPTED", List.of("已接单"),
            "ARRIVED", List.of("待取餐")
    );

    private OrderSearch() {
    }

    public static int pageSize(int size) {
        if (size == 5 || size == 10 || size == 20) {
            return size;
        }
        return DEFAULT_SIZE;
    }

    static boolean statusHit(String status, String raw, String needle) {
        if (status == null || status.isBlank()) {
            return false;
        }
        if (status.toLowerCase(Locale.ROOT).contains(needle)) {
            return true;
        }
        String cn = STATUS_CN.getOrDefault(status, "");
        if (cn.isEmpty()) {
            return false;
        }
        if (cn.contains(raw) || cn.toLowerCase(Locale.ROOT).contains(needle)) {
            return true;
        }
        String compact = cn.replace("商家", "").replace("骑手", "").replace("·", "");
        if (compact.contains(raw) || compact.toLowerCase(Locale.ROOT).contains(needle)) {
            return true;
        }
        List<String> aliases = STATUS_ALIASES.getOrDefault(status, List.of());
        for (String alias : aliases) {
            if (alias.contains(raw) || raw.contains(alias) || alias.toLowerCase(Locale.ROOT).contains(needle)) {
                return true;
            }
        }
        return false;
    }

    public static int pageNo(Integer page) {
        if (page == null || page < 1) {
            return 1;
        }
        return page;
    }

    /**
     * 仅当 q 唯一命中某个状态枚举或中文全称时下推 SQL；模糊别名（如「待接单」）不下推。
     */
    public static String exactStatus(String q) {
        if (q == null || q.isBlank()) {
            return null;
        }
        String raw = q.trim();
        for (Map.Entry<String, String> e : STATUS_CN.entrySet()) {
            if (e.getKey().equalsIgnoreCase(raw) || e.getValue().equals(raw)) {
                return e.getKey();
            }
        }
        return null;
    }

    public static boolean matches(long id, String status, String address, String snapshot, String q) {
        if (q == null || q.isBlank()) {
            return true;
        }
        String raw = q.trim();
        String needle = raw.toLowerCase(Locale.ROOT);
        String idStr = Long.toString(id);
        if (idStr.equals(raw) || (raw.chars().allMatch(Character::isDigit) && idStr.startsWith(raw))) {
            return true;
        }
        if (statusHit(status, raw, needle)) {
            return true;
        }
        if (address != null && address.toLowerCase(Locale.ROOT).contains(needle)) {
            return true;
        }
        return snapshot != null && snapshot.toLowerCase(Locale.ROOT).contains(needle);
    }

    public static <T> Slice<T> slice(List<T> all, int page, int size, java.util.function.ToLongFunction<T> idOf) {
        int total = all.size();
        int p = Math.max(1, page);
        int from = (p - 1) * size;
        List<T> items = from >= total ? List.of() : all.subList(from, Math.min(from + size, total));
        Long next = items.isEmpty() ? null : idOf.applyAsLong(items.get(items.size() - 1));
        boolean hasNext = from + items.size() < total;
        boolean hasPrev = p > 1;
        return new Slice<>(List.copyOf(items), p, size, total, hasNext, hasPrev, next);
    }

    public record Slice<T>(List<T> items, int page, int size, int total, boolean hasNext, boolean hasPrev, Long nextCursor) {
    }
}

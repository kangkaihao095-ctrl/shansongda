package com.shansuda.order.stats;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 商家统计日滚表，直连 ssd_order_0（不走订单分片）。
 */
@Component
public class MerchantStatDayStore {

    private static final Logger log = LoggerFactory.getLogger(MerchantStatDayStore.class);
    private static final String URL =
            "jdbc:mysql://127.0.0.1:3316/ssd_order_0?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=UTF-8&connectionCollation=utf8mb4_unicode_ci&serverTimezone=Asia/Shanghai";
    private static final String USER = "shansuda";
    private static final String PASS = "shansuda";

    public void onPaid(long merchantId, int gmvCents, Instant at) {
        bump(merchantId, day(at), 1, Math.max(0, gmvCents), 0, 0);
    }

    public void onCompleted(long merchantId, Instant at) {
        bump(merchantId, day(at), 0, 0, 1, 0);
    }

    public void onRefunded(long merchantId, int refundCents, Instant at) {
        bump(merchantId, day(at), 0, 0, 0, Math.max(0, refundCents));
    }

    public List<MerchantStatsAggregator.DayPoint> load(long merchantId, LocalDate from, LocalDate to) {
        List<MerchantStatsAggregator.DayPoint> rows = new ArrayList<>();
        String sql = "SELECT day, orders, gmv, completed, refund FROM merchant_stat_day WHERE merchant_id = ? AND day >= ? AND day <= ? ORDER BY day";
        try (Connection c = DriverManager.getConnection(URL, USER, PASS);
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, merchantId);
            ps.setDate(2, Date.valueOf(from));
            ps.setDate(3, Date.valueOf(to));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new MerchantStatsAggregator.DayPoint(
                            rs.getDate("day").toLocalDate().toString(),
                            rs.getInt("orders"),
                            rs.getLong("gmv"),
                            rs.getInt("completed"),
                            rs.getLong("refund")));
                }
            }
        } catch (Exception ex) {
            log.debug("读商家滚表失败，回退 scatter: {}", ex.getMessage());
        }
        return rows;
    }

    public boolean hasRows(List<MerchantStatsAggregator.DayPoint> rows) {
        if (rows == null || rows.isEmpty()) {
            return false;
        }
        for (MerchantStatsAggregator.DayPoint row : rows) {
            if (row.orderCount() > 0 || row.gmvCents() > 0 || row.completedCount() > 0 || row.refundCents() > 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * 滚表覆盖整个窗口才可信。历史种子只写分片订单、不写滚表时，近 30 日会只剩最近几天非 0。
     */
    public boolean coversWindow(List<MerchantStatsAggregator.DayPoint> rows, LocalDate from, LocalDate to,
                                long scatterGmv) {
        if (!hasRows(rows) || from == null || to == null || from.isAfter(to)) {
            return false;
        }
        long rollGmv = 0;
        LocalDate earliest = null;
        for (MerchantStatsAggregator.DayPoint row : rows) {
            rollGmv += row.gmvCents();
            if (row.orderCount() <= 0 && row.gmvCents() <= 0) {
                continue;
            }
            LocalDate day = parseDay(row.date());
            if (day != null && (earliest == null || day.isBefore(earliest))) {
                earliest = day;
            }
        }
        if (earliest == null || earliest.isAfter(from.plusDays(2))) {
            return false;
        }
        return scatterGmv <= 0 || rollGmv >= (long) (scatterGmv * 0.85);
    }

    private static LocalDate parseDay(String raw) {
        if (raw == null || raw.length() < 10) {
            return null;
        }
        try {
            return LocalDate.parse(raw.substring(0, 10));
        } catch (Exception ex) {
            return null;
        }
    }

    private void bump(long merchantId, LocalDate day, int orders, int gmv, int completed, int refund) {
        String sql = """
                INSERT INTO merchant_stat_day (merchant_id, day, orders, gmv, completed, refund)
                VALUES (?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                  orders = orders + VALUES(orders),
                  gmv = gmv + VALUES(gmv),
                  completed = completed + VALUES(completed),
                  refund = refund + VALUES(refund)
                """;
        try (Connection c = DriverManager.getConnection(URL, USER, PASS);
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, merchantId);
            ps.setDate(2, Date.valueOf(day));
            ps.setInt(3, orders);
            ps.setInt(4, gmv);
            ps.setInt(5, completed);
            ps.setInt(6, refund);
            ps.executeUpdate();
        } catch (Exception ex) {
            log.debug("滚表增量失败 merchant={} day={}: {}", merchantId, day, ex.getMessage());
        }
    }

    private static LocalDate day(Instant at) {
        Instant ts = at == null ? Instant.now() : at;
        return LocalDate.ofInstant(ts, MerchantStatsAggregator.ZONE);
    }
}

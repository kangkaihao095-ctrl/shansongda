package com.shansuda.order;

import com.shansuda.order.stats.MerchantStatsAggregator;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MerchantStatsAggregatorTest {

    @Test
    void aggregatesTodayAndFillsEmptyDays() {
        LocalDate today = LocalDate.of(2026, 9, 4);
        var rows = List.of(
                new MerchantStatsAggregator.OrderRow("COMPLETED", 2500, 600,
                        ZonedDateTime.of(today, LocalTime.of(12, 0), MerchantStatsAggregator.ZONE).toInstant()),
                new MerchantStatsAggregator.OrderRow("DELIVERING", 1800, 400,
                        ZonedDateTime.of(today, LocalTime.of(13, 0), MerchantStatsAggregator.ZONE).toInstant()),
                new MerchantStatsAggregator.OrderRow("COMPLETED", 900, 300,
                        ZonedDateTime.of(today.minusDays(2), LocalTime.of(18, 0), MerchantStatsAggregator.ZONE).toInstant())
        );
        MerchantStatsAggregator.Stats stats = MerchantStatsAggregator.aggregate(rows, today, 7);
        assertEquals(2, stats.todayOrders());
        assertEquals(2500 + 600 + 1800 + 400, stats.todayGmvCents());
        assertEquals(1, stats.inProgress());
        assertEquals(1, stats.completed());
        assertEquals(0, stats.refundCents());
        assertEquals("day", stats.grain());
        assertEquals("7d", stats.range());
        assertEquals(7, stats.series().size());
        assertEquals(today.minusDays(6).toString(), stats.series().get(0).date());
        assertEquals(today.toString(), stats.series().get(6).date());
        assertEquals(1, stats.series().get(4).completedCount());
    }

    @Test
    void yearAggregatesByMonthAndCountsRefund() {
        LocalDate today = LocalDate.of(2026, 9, 7);
        var rows = List.of(
                new MerchantStatsAggregator.OrderRow("COMPLETED", 2000, 500,
                        ZonedDateTime.of(today.minusMonths(2).withDayOfMonth(3), LocalTime.NOON, MerchantStatsAggregator.ZONE).toInstant()),
                new MerchantStatsAggregator.OrderRow("REFUNDED", 1800, 400,
                        ZonedDateTime.of(today, LocalTime.of(10, 0), MerchantStatsAggregator.ZONE).toInstant(), 2200)
        );
        MerchantStatsAggregator.Stats stats = MerchantStatsAggregator.aggregate(rows, today, 365);
        assertEquals("month", stats.grain());
        assertEquals("1y", stats.range());
        assertEquals(12, stats.series().size());
        assertEquals(YearMonth.from(today.minusMonths(11)).toString(), stats.series().get(0).date());
        assertEquals(YearMonth.from(today).toString(), stats.series().get(11).date());
        assertEquals(2200, stats.refundCents());
        assertEquals(1, stats.todayOrders());
        assertTrue(stats.series().stream().anyMatch(p -> p.orderCount() > 0 && p.gmvCents() > 0));
    }

    @Test
    void resolveRange() {
        assertEquals(7, MerchantStatsAggregator.resolveDays("7d", null));
        assertEquals(30, MerchantStatsAggregator.resolveDays("30d", 7));
        assertEquals(365, MerchantStatsAggregator.resolveDays("1y", null));
        assertEquals(365, MerchantStatsAggregator.resolveDays(null, 365));
        assertEquals(30, MerchantStatsAggregator.resolveDays(null, 30));
    }
}

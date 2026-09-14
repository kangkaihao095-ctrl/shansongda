package com.shansuda.order;

import com.shansuda.order.stats.MerchantReport;
import com.shansuda.order.stats.MerchantStatsAggregator;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MerchantReportTest {

    @Test
    void summaryUsesAggregatedOrdersNotSla() {
        LocalDate today = LocalDate.of(2026, 9, 7);
        var rows = List.of(
                new MerchantStatsAggregator.OrderRow("COMPLETED", 2500, 600,
                        ZonedDateTime.of(today, LocalTime.of(12, 0), MerchantStatsAggregator.ZONE).toInstant()),
                new MerchantStatsAggregator.OrderRow("REFUNDED", 1800, 400,
                        ZonedDateTime.of(today.minusDays(1), LocalTime.of(18, 0), MerchantStatsAggregator.ZONE).toInstant(), 2200)
        );
        MerchantStatsAggregator.Stats stats = MerchantStatsAggregator.aggregate(rows, today, 7);
        var top = List.of(new MerchantReport.SkuHit("杨枝甘露", 4), new MerchantReport.SkuHit("草莓杯", 2));
        String text = MerchantReport.summary(stats, top);
        assertTrue(text.contains("近七日"));
        assertTrue(text.contains("杨枝甘露"));
        assertTrue(text.contains("高峰日"));
        assertTrue(text.contains("客单价"));
        assertFalse(text.contains("SLA"));
        assertFalse(text.contains("P95"));
        var extras = MerchantReport.extras(stats, top);
        assertTrue(extras.containsKey("avgPayCents"));
        assertTrue(extras.containsKey("refundRate"));
    }

    @Test
    void csvHasHeaderAndUtf8Bom() {
        LocalDate today = LocalDate.of(2026, 9, 7);
        MerchantStatsAggregator.Stats stats = MerchantStatsAggregator.aggregate(List.of(), today, 7);
        byte[] csv = MerchantReport.csv(stats);
        String text = new String(csv, java.nio.charset.StandardCharsets.UTF_8);
        assertEquals('\uFEFF', text.charAt(0));
        assertTrue(text.contains("日期,单量,GMV,完成,退款"));
        assertEquals(8, text.split("\n").length);
    }

    @Test
    void csvThirtyDaysHasThirtyRows() {
        LocalDate today = LocalDate.of(2026, 9, 11);
        MerchantStatsAggregator.Stats stats = MerchantStatsAggregator.aggregate(List.of(), today, 30);
        byte[] csv = MerchantReport.csv(stats);
        String text = new String(csv, java.nio.charset.StandardCharsets.UTF_8);
        assertEquals(31, text.split("\n").length);
        assertTrue(text.contains(today.minusDays(29).toString()));
        assertTrue(text.contains(today.toString()));
    }

    @Test
    void topSkusRanksByQty() {
        var hits = MerchantReport.topSkus(List.of(
                Map.of("items", List.of(Map.of("name", "茶", "qty", 2), Map.of("name", "饭", "qty", 1))),
                Map.of("items", List.of(Map.of("name", "茶", "qty", 3)))
        ), 3);
        assertEquals("茶", hits.get(0).name());
        assertEquals(5, hits.get(0).qty());
        assertEquals("饭", hits.get(1).name());
    }
}

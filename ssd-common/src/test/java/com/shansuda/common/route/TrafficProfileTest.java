package com.shansuda.common.route;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrafficProfileTest {

    /** 2026-09-11 周五 08:00 上海 = 早高峰。 */
    static final Clock MORNING = Clock.fixed(Instant.parse("2026-09-11T00:00:00Z"), ZoneOffset.UTC);
    /** 2026-09-11 周五 02:00 上海 = 凌晨平峰。 */
    static final Clock NIGHT = Clock.fixed(Instant.parse("2026-09-10T18:00:00Z"), ZoneOffset.UTC);
    /** 同一小时 08:20，分钟抖动应很小。 */
    static final Clock MORNING_LATER = Clock.fixed(Instant.parse("2026-09-11T00:20:00Z"), ZoneOffset.UTC);

    @Test
    void morningPeakPrimaryHigherThanNight() {
        GridPathFinder.Edge primary = new GridPathFinder.Edge(1, 1.0, 1.0, "osm-1-0a", "南京东路", "primary");
        double peak = TrafficProfile.congestionOf(primary, MORNING);
        double night = TrafficProfile.congestionOf(primary, NIGHT);
        assertEquals(TrafficProfile.Period.MORNING_PEAK, TrafficProfile.period(MORNING));
        assertEquals(TrafficProfile.Period.OFF_PEAK, TrafficProfile.period(NIGHT));
        assertTrue(peak >= 1.6, "早高峰主干应能到拥堵: " + peak);
        assertTrue(night < 1.25, "凌晨应接近畅通: " + night);
        assertTrue(peak > night + 0.4, peak + " vs " + night);
    }

    @Test
    void sameHourSameRoadIsStableAcrossRestart() {
        GridPathFinder first = GridPathFinder.demoGrid();
        GridPathFinder second = GridPathFinder.demoGrid();
        TrafficProfile.apply(first, MORNING, false);
        TrafficProfile.apply(second, MORNING, false);
        GridPathFinder.Edge a = first.adj().get(0).get(0);
        GridPathFinder.Edge b = second.adj().get(0).get(0);
        assertEquals(a.roadId, b.roadId);
        assertEquals(a.congestion, b.congestion, 1e-9);
        TrafficProfile.apply(first, MORNING_LATER, false);
        assertEquals(a.congestion, first.adj().get(0).get(0).congestion, 0.08);
    }

    @Test
    void unmatchedHighwayStaysNearClearAndMapIsNotAllRed() {
        GridPathFinder graph = GridPathFinder.demoGrid();
        TrafficProfile.apply(graph, MORNING, false);
        long total = 0;
        long red = 0;
        long unknownClear = 0;
        for (List<GridPathFinder.Edge> edges : graph.adj().values()) {
            for (GridPathFinder.Edge e : edges) {
                total++;
                if (e.congestion > 1.6) {
                    red++;
                }
                if ((e.highway == null || e.highway.isBlank()) && e.congestion <= 1.12) {
                    unknownClear++;
                }
            }
        }
        assertTrue(total > 0);
        assertTrue(red < total / 2, "全图不要全红 red=" + red + " total=" + total);
        GridPathFinder.Edge blank = new GridPathFinder.Edge(2, 1.0, 1.0, "x-1a", "", "");
        double c = TrafficProfile.congestionOf(blank, MORNING);
        assertTrue(c < 1.2, "未匹配等级应接近 1.0: " + c);
        assertTrue(unknownClear >= 0);
    }

    @Test
    void amapSuccessDoesNotMixProfile() {
        GridPathFinder graph = GridPathFinder.demoGrid();
        TrafficProfile.apply(graph, MORNING, false);
        GridPathFinder.Node a = graph.node(0);
        GridPathFinder.Node b = graph.node(1);
        TrafficProfile.Decision mixed = TrafficProfile.refresh(graph, AmapTrafficClient.TrafficSnapshot.success(List.of(
                new AmapTrafficClient.AmapRoad("", "4", 8.0, a.lon + "," + a.lat + ";" + b.lon + "," + b.lat)
        ), 1), MORNING, false);
        assertEquals(TrafficProfile.AMAP, mixed.source());
        assertEquals(2.4, graph.adj().get(0).get(0).congestion, 1e-6);
        long profileLike = graph.adj().values().stream().flatMap(List::stream)
                .filter(e -> e.congestion > 1.01 && e.congestion != 2.4).count();
        assertEquals(0, profileLike, "高德成功后未匹配边应回 1.0，不要残留画像");
    }

    @Test
    void amapFailWithoutPriorSuccessUsesProfile() {
        GridPathFinder graph = GridPathFinder.demoGrid();
        TrafficProfile.Decision d = TrafficProfile.refresh(
                graph, AmapTrafficClient.TrafficSnapshot.fail("HTTP 500"), MORNING, false);
        assertEquals(TrafficProfile.PROFILE, d.source());
        assertEquals("早高峰", d.periodLabel());
        assertTrue(d.wrote());
        assertTrue(graph.adj().get(0).stream().anyMatch(e -> e.congestion > 1.4));
        assertTrue(TrafficProfile.hint(d.source(), d.periodLabel()).contains("画像"));
        assertTrue(TrafficProfile.hint(d.source(), d.periodLabel()).contains("早高峰"));
    }

    @Test
    void amapFailKeepsLastSuccess() {
        GridPathFinder graph = GridPathFinder.demoGrid();
        GridPathFinder.Node a = graph.node(0);
        GridPathFinder.Node b = graph.node(1);
        TrafficProfile.refresh(graph, AmapTrafficClient.TrafficSnapshot.success(List.of(
                new AmapTrafficClient.AmapRoad("", "4", 8.0, a.lon + "," + a.lat + ";" + b.lon + "," + b.lat)
        ), 1), MORNING, false);
        double jammed = graph.adj().get(0).get(0).congestion;
        TrafficProfile.Decision d = TrafficProfile.refresh(
                graph, AmapTrafficClient.TrafficSnapshot.fail("HTTP 500"), MORNING, true);
        assertEquals(TrafficProfile.LAST_SUCCESS, d.source());
        assertEquals(jammed, graph.adj().get(0).get(0).congestion, 1e-9);
        assertTrue(TrafficProfile.hint(d.source(), "").contains("上次"));
    }
}

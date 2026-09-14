package com.shansuda.dispatch;

import com.shansuda.common.route.CongestionAggregator;
import com.shansuda.common.route.GridPathFinder;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PathFinderTest {

    @Test
    void congestionFromPositionsNotRandom() {
        GridPathFinder graph = GridPathFinder.demoGrid();
        GridPathFinder.Node a = graph.node(0);
        List<double[]> hits = List.of(
                new double[]{a.lat, a.lon},
                new double[]{a.lat, a.lon},
                new double[]{a.lat, a.lon}
        );
        var rows = CongestionAggregator.apply(graph, hits);
        assertFalse(rows.isEmpty());
        boolean raised = graph.adj().get(0).stream().anyMatch(e -> e.congestion > 1.01);
        assertTrue(raised);
        var again = CongestionAggregator.apply(graph, List.of());
        assertTrue(again.stream().noneMatch(r -> r.get("roadId") == null));
    }
}

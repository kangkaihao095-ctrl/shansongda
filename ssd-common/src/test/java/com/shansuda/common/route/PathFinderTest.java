package com.shansuda.common.route;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PathFinderTest {

    @Test
    void astarWhenBothHaveCoordinates() {
        GridPathFinder graph = GridPathFinder.demoGrid();
        GridPathFinder.Path path = graph.shortest(0, 9);
        assertEquals("ASTAR", path.algorithm);
        assertTrue(path.nodeIds.size() >= 2);
        assertEquals(0, path.nodeIds.get(0));
        assertEquals(9, path.nodeIds.get(path.nodeIds.size() - 1));
        assertTrue(path.cost > 0);
    }

    @Test
    void dijkstraWhenMissingCoordinates() {
        GridPathFinder graph = new GridPathFinder();
        graph.addNode(new GridPathFinder.Node(1, null, null));
        graph.addNode(new GridPathFinder.Node(2, null, null));
        graph.addNode(new GridPathFinder.Node(3, null, null));
        graph.addEdge(1, new GridPathFinder.Edge(2, 1, 1, "a"));
        graph.addEdge(2, new GridPathFinder.Edge(3, 1, 1, "b"));
        GridPathFinder.Path path = graph.shortest(1, 3);
        assertEquals("DIJKSTRA", path.algorithm);
        assertEquals(2.0, path.cost);
    }

    @Test
    void shanghaiOsmAstarFollowsRealCoords() {
        GridPathFinder graph = OsmGraphLoader.load();
        assertTrue(graph.nodes().size() >= 50);
        GridPathFinder.Node merchant = graph.nearest(31.2380, 121.4840);
        GridPathFinder.Node user = graph.nearest(31.2240, 121.4690);
        GridPathFinder.Node rider = graph.nearest(31.2304, 121.4737);
        assertTrue(merchant.hasCoord() && user.hasCoord() && rider.hasCoord());
        GridPathFinder.Path toShop = graph.shortest(rider.id, merchant.id);
        GridPathFinder.Path toUser = graph.shortest(merchant.id, user.id);
        assertEquals("ASTAR", toShop.algorithm);
        assertEquals("ASTAR", toUser.algorithm);
        assertTrue(toShop.nodeIds.size() >= 3);
        assertTrue(toUser.nodeIds.size() >= 3);
        assertTrue(toShop.cost > 0 && toUser.cost > 0);
    }
}

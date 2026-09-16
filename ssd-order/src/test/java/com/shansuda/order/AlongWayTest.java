package com.shansuda.order;

import com.shansuda.order.route.AlongWay;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlongWayTest {

    @Test
    void sameDirectionIsAlongWay() {
        assertTrue(AlongWay.along(
                31.230, 121.470,
                31.250, 121.470,
                31.235, 121.470,
                31.240, 121.470));
    }

    @Test
    void oppositeDirectionIsNotAlongWay() {
        assertFalse(AlongWay.along(
                31.230, 121.470,
                31.250, 121.470,
                31.210, 121.470,
                31.200, 121.470));
    }

    @Test
    void smallDetourStillCountsAsAlongWay() {
        assertTrue(AlongWay.along(
                31.230, 121.470,
                31.238, 121.484,
                31.232, 121.475,
                31.234, 121.480));
    }
}

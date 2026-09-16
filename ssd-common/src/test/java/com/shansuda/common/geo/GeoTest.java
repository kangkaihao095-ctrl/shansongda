package com.shansuda.common.geo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeoTest {

    @Test
    void roundKmAndNullCoords() {
        assertEquals(1.3, Geo.roundKm(1.26), 1e-9);
        assertEquals(-1, Geo.roundKm(Double.POSITIVE_INFINITY), 1e-9);
        assertFalse(Geo.inRange(31.23, 121.47, null, null, Geo.DEFAULT_MAX_KM));
        assertTrue(Geo.inRange(31.238, 121.484, 31.238, 121.484, 5));
    }

    @Test
    void boundingBoxContainsCenterAndExcludesFarPoint() {
        double[] box = Geo.boundingBox(31.230, 121.470, 800);
        assertTrue(Geo.inBoundingBox(31.230, 121.470, box));
        assertFalse(Geo.inBoundingBox(31.280, 121.540, box));
        assertTrue(Geo.inBoundingBox(31.230, 121.470, null));
        double[] empty = Geo.boundingBox(31.230, 121.470, 0);
        assertEquals(31.230, empty[0], 1e-9);
        assertEquals(31.230, empty[1], 1e-9);
    }
}

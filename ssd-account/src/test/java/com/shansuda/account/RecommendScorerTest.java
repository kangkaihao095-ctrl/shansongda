package com.shansuda.account;

import com.shansuda.account.catalog.RecommendScorer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RecommendScorerTest {

    @Test
    void nearerShopScoresHigher() {
        double near = RecommendScorer.score(31.23, 121.47, 31.231, 121.472, 0, 0, 10, 100, 4.8, true, false);
        double far = RecommendScorer.score(31.23, 121.47, 31.30, 121.55, 0, 0, 10, 100, 4.8, true, false);
        assertTrue(near > far);
    }

    @Test
    void affinityBoostsMatchingCategoryWhenHistoryExists() {
        double hit = RecommendScorer.score(31.23, 121.47, 31.23, 121.47, 10, 10, 20, 100, 4.6, true, true);
        double miss = RecommendScorer.score(31.23, 121.47, 31.23, 121.47, 0, 10, 20, 100, 4.6, true, true);
        assertTrue(hit > miss);
    }

    @Test
    void offlineIsDownrankedButStillPositive() {
        double on = RecommendScorer.score(31.23, 121.47, 31.23, 121.47, 0, 0, 50, 100, 4.8, true, false);
        double off = RecommendScorer.score(31.23, 121.47, 31.23, 121.47, 0, 0, 50, 100, 4.8, false, false);
        assertTrue(on > off);
        assertTrue(off > 0);
    }

    @Test
    void badRatingIsPenalizedHarderThanLinear() {
        double good = RecommendScorer.score(31.23, 121.47, 31.23, 121.47, 0, 0, 50, 100, 4.9, true, false);
        double bad = RecommendScorer.score(31.23, 121.47, 31.23, 121.47, 0, 0, 50, 100, 3.0, true, false);
        assertTrue(good > bad);
        assertTrue(RecommendScorer.ratingScore(3.0) < (3.0 / 5.0) * 0.7);
    }

    @Test
    void waitanAndWujiaochangAreBeyondFiveKm() {
        double km = RecommendScorer.haversineKm(31.2397, 121.4903, 31.2994, 121.5145);
        assertTrue(km > 5.0);
        assertTrue(com.shansuda.common.geo.Geo.inRange(31.2397, 121.4903, 31.2380, 121.4840, 5.0));
        assertTrue(!com.shansuda.common.geo.Geo.inRange(31.2397, 121.4903, 31.2994, 121.5145, 5.0));
    }
}

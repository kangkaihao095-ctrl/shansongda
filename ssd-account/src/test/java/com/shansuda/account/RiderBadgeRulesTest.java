package com.shansuda.account;

import com.shansuda.account.rider.RiderBadgeRules;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RiderBadgeRulesTest {

    @Test
    void seedRiderLightsAllFour() {
        List<Map<String, Object>> badges = RiderBadgeRules.badges(186, 4.92, 164, 0.978, 4400);
        assertEquals(4, badges.size());
        assertTrue(earned(badges, "HUNDRED"));
        assertTrue(earned(badges, "PRAISE"));
        assertTrue(earned(badges, "ON_TIME"));
        assertTrue(earned(badges, "TIP_STAR"));
    }

    @Test
    void newRiderHasNoBadges() {
        List<Map<String, Object>> badges = RiderBadgeRules.badges(0, 4.8, 0, 0.92, 0);
        assertFalse(earned(badges, "HUNDRED"));
        assertFalse(earned(badges, "PRAISE"));
        assertFalse(earned(badges, "ON_TIME"));
        assertFalse(earned(badges, "TIP_STAR"));
    }

    @SuppressWarnings("unchecked")
    private static boolean earned(List<Map<String, Object>> badges, String code) {
        return badges.stream()
                .filter(b -> code.equals(b.get("code")))
                .map(b -> Boolean.TRUE.equals(b.get("earned")))
                .findFirst()
                .orElse(false);
    }
}

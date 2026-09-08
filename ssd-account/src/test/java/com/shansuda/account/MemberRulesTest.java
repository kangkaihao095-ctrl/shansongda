package com.shansuda.account;

import com.shansuda.account.catalog.MemberRules;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MemberRulesTest {

    @Test
    void levelsFollowNetPaidYuan() {
        assertEquals(1, MemberRules.level(0));
        assertEquals(1, MemberRules.level(19999));
        assertEquals(2, MemberRules.level(20000));
        assertEquals(3, MemberRules.level(80000));
        assertEquals(7, MemberRules.level(3_000_000));
        assertEquals("金卡", MemberRules.title(3));
        assertEquals(2000, MemberRules.nextThresholdYuan(3));
        assertEquals(-1, MemberRules.nextThresholdYuan(7));
    }

    @Test
    void plansCoverMonthQuarterYearAuto() {
        assertEquals(31, MemberRules.days("MONTH"));
        assertEquals(93, MemberRules.days("QUARTER"));
        assertEquals(365, MemberRules.days("YEAR"));
        assertEquals(1500, MemberRules.priceCents("MONTH"));
        assertEquals(4000, MemberRules.priceCents("QUARTER"));
        assertEquals(12800, MemberRules.priceCents("YEAR"));
        assertEquals(1200, MemberRules.priceCents("AUTO_MONTH"));
        assertEquals("连续包月", MemberRules.planLabel("AUTO_MONTH"));
        assertEquals(4, MemberRules.catalog().size());
    }
}

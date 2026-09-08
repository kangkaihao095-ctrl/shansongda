package com.shansuda.account;

import com.shansuda.account.catalog.CouponIcons;
import com.shansuda.account.domain.CouponTemplate;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CouponIconsTest {

    @Test
    void iconsFollowSceneAndThreshold() {
        CouponTemplate free = tpl("HOME_0_3", "HOME", "AMOUNT", 0, false);
        CouponTemplate minus = tpl("FREQ_25_5", "FREQUENT", "AMOUNT", 2500, false);
        CouponTemplate member = tpl("MEMBER_5", "MEMBER", "AMOUNT", 0, true);
        CouponTemplate food = tpl("HOME_FOOD_25_8", "HOME", "AMOUNT", 2500, false);
        CouponTemplate percent = tpl("PERCENT_10", "RETURN", "PERCENT", 1500, false);
        CouponTemplate newbie = tpl("NEWCOMER_15", "NEWCOMER", "AMOUNT", 0, false);
        assertEquals("free", CouponIcons.resolve(free));
        assertEquals("minus", CouponIcons.resolve(minus));
        assertEquals("member", CouponIcons.resolve(member));
        assertEquals("category", CouponIcons.resolve(food));
        assertEquals("percent", CouponIcons.resolve(percent));
        assertEquals("newcomer", CouponIcons.resolve(newbie));
    }

    @Test
    void ttlByScene() {
        assertEquals(7 * 86400, CouponIcons.validSeconds("NEWCOMER"));
        assertEquals(86400, CouponIcons.validSeconds("LOGIN"));
        assertEquals(3 * 86400, CouponIcons.validSeconds("HOME"));
        assertEquals("/images/coupons/member.svg", CouponIcons.url("member"));
    }

    private static CouponTemplate tpl(String code, String scene, String type, int min, boolean member) {
        CouponTemplate t = new CouponTemplate();
        t.setCode(code);
        t.setScene(scene);
        t.setType(type);
        t.setMinSpendCents(min);
        t.setMemberOnly(member);
        return t;
    }
}

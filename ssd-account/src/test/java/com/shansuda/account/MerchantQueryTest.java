package com.shansuda.account;

import com.shansuda.account.catalog.MerchantQuery;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MerchantQueryTest {

    @Test
    void matchesShopNameCategoryAndAddress() {
        assertTrue(MerchantQuery.matches("闪送达鲜生·南京东路", "fresh", "黄浦区南京东路", "鲜生"));
        assertTrue(MerchantQuery.matches("闪送达鲜生·南京东路", "fresh", "黄浦区南京东路", "生鲜"));
        assertTrue(MerchantQuery.matches("闪送达鲜生·南京东路", "fresh", "黄浦区南京东路", "南京东路"));
        assertTrue(MerchantQuery.matches("康安大药房·西藏中路", "pharma", "黄浦区西藏中路", "医药"));
        assertFalse(MerchantQuery.matches("闪送达鲜生·南京东路", "fresh", "黄浦区南京东路", "火锅店"));
        assertTrue(MerchantQuery.matchesSku("杨枝甘露", "杨枝"));
        assertFalse(MerchantQuery.matchesSku("杨枝甘露", "火锅"));
        assertTrue(MerchantQuery.matches("任意店", "food", "外滩", ""));
    }

    @Test
    void categoryNameIsChinese() {
        assertEquals("美食", MerchantQuery.categoryName("food"));
        assertEquals("生鲜果蔬", MerchantQuery.categoryName("fresh"));
    }
}

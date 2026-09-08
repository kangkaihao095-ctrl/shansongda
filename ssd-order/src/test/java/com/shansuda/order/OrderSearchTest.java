package com.shansuda.order;

import com.shansuda.order.query.OrderSearch;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderSearchTest {

    private static final String SNAP = "{\"items\":[{\"name\":\"时令水果拼盘\"}],\"shopName\":\"闪送达鲜生·南京东路\"}";

    @Test
    void sizeOnlyAllowsFiveTenTwenty() {
        assertEquals(5, OrderSearch.pageSize(5));
        assertEquals(10, OrderSearch.pageSize(10));
        assertEquals(20, OrderSearch.pageSize(20));
        assertEquals(10, OrderSearch.pageSize(15));
        assertEquals(10, OrderSearch.pageSize(50));
        assertEquals(10, OrderSearch.pageSize(0));
    }

    @Test
    void matchesIdPrefixAndStatusAndSnapshot() {
        assertTrue(OrderSearch.matches(123456L, "PAID", "外滩", SNAP, "123"));
        assertTrue(OrderSearch.matches(123456L, "PAID", "外滩", SNAP, "123456"));
        assertTrue(OrderSearch.matches(1L, "DELIVERING", "外滩", SNAP, "配送中"));
        assertTrue(OrderSearch.matches(1L, "PAID", "外滩", SNAP, "PAID"));
        assertTrue(OrderSearch.matches(1L, "MERCHANT_PENDING", "外滩", SNAP, "待商家接单"));
        assertTrue(OrderSearch.matches(1L, "PAID", "外滩", SNAP, "待接单"));
        assertTrue(OrderSearch.matches(1L, "PAID", "外滩", SNAP, "备餐中"));
        assertTrue(OrderSearch.matches(1L, "PAID", "外滩", SNAP, "商家已接单"));
        assertTrue(OrderSearch.matches(1L, "COMPLETED", "外滩", SNAP, "已送达"));
        assertTrue(OrderSearch.matches(1L, "COMPLETED", "上海市黄浦区外滩源", SNAP, "外滩"));
        assertTrue(OrderSearch.matches(1L, "COMPLETED", "地址", SNAP, "水果"));
        assertTrue(OrderSearch.matches(1L, "COMPLETED", "地址", SNAP, "鲜生"));
        assertFalse(OrderSearch.matches(1L, "CREATED", "外滩", SNAP, "西瓜"));
        assertTrue(OrderSearch.matches(1L, "CREATED", "外滩", SNAP, " "));
    }

    @Test
    void slicesPagesWithoutSqlOffset() {
        List<Long> ids = List.of(9L, 8L, 7L, 6L, 5L);
        OrderSearch.Slice<Long> first = OrderSearch.slice(ids, 1, 2, Long::longValue);
        assertEquals(List.of(9L, 8L), first.items());
        assertTrue(first.hasNext());
        assertFalse(first.hasPrev());
        assertEquals(5, first.total());
        OrderSearch.Slice<Long> last = OrderSearch.slice(ids, 3, 2, Long::longValue);
        assertEquals(List.of(5L), last.items());
        assertFalse(last.hasNext());
        assertTrue(last.hasPrev());
        assertEquals(5L, last.nextCursor());
    }
}

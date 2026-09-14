package com.shansuda.account.catalog;

import java.util.Locale;
import java.util.Map;

/** 首页/分类搜商家：店名、品类键/中文、地址。 */
public final class MerchantQuery {

    public static final Map<String, String> CATEGORY_NAMES = Map.of(
            "food", "美食",
            "dessert", "甜点饮品",
            "market", "超市便利",
            "fresh", "生鲜果蔬",
            "pharma", "医药健康",
            "flower", "鲜花蛋糕",
            "tea", "下午茶",
            "errand", "跑腿"
    );

    private MerchantQuery() {
    }

    public static String categoryName(String key) {
        if (key == null || key.isBlank()) {
            return "";
        }
        return CATEGORY_NAMES.getOrDefault(key, key);
    }

    public static boolean matches(String shopName, String category, String address, String q) {
        String needle = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        if (needle.isEmpty()) {
            return true;
        }
        return contains(shopName, needle)
                || contains(category, needle)
                || contains(categoryName(category), needle)
                || contains(address, needle);
    }

    public static boolean matchesSku(String skuName, String q) {
        String needle = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        if (needle.isEmpty() || skuName == null) {
            return false;
        }
        return skuName.toLowerCase(Locale.ROOT).contains(needle);
    }

    private static boolean contains(String raw, String needle) {
        return raw != null && raw.toLowerCase(Locale.ROOT).contains(needle);
    }
}

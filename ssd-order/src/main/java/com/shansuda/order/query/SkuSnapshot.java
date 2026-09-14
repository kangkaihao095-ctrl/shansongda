package com.shansuda.order.query;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 完成单 skuSnapshot 回填购物车；再来一单要求 items 非空。 */
public final class SkuSnapshot {

    private SkuSnapshot() {
    }

    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> items(Object snapshot) {
        if (!(snapshot instanceof Map<?, ?> map)) {
            return List.of();
        }
        Object raw = map.get("items");
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object row : list) {
            if (!(row instanceof Map<?, ?> item)) {
                continue;
            }
            Object skuId = item.get("skuId");
            if (!(skuId instanceof Number)) {
                continue;
            }
            Map<String, Object> copy = new LinkedHashMap<>();
            copy.put("skuId", ((Number) skuId).longValue());
            copy.put("name", item.get("name"));
            copy.put("qty", item.get("qty") instanceof Number n ? Math.max(1, n.intValue()) : 1);
            copy.put("priceCents", item.get("priceCents"));
            copy.put("imageUrl", item.get("imageUrl"));
            copy.put("spec", item.get("spec"));
            out.add(copy);
        }
        return out;
    }

    public static boolean hasItems(Object snapshot) {
        return !items(snapshot).isEmpty();
    }
}

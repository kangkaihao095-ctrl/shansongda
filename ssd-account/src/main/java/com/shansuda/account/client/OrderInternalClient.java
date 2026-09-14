package com.shansuda.account.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/** 读用户完成单商家频次。订单服务不可用时返回空，推荐走距离+评分+销量兜底。 */
@Component
public class OrderInternalClient {

    private static final Logger log = LoggerFactory.getLogger(OrderInternalClient.class);

    private final RestTemplate restTemplate;
    private final String base;

    public OrderInternalClient(@Value("${ssd.client.order:http://127.0.0.1:18083}") String base) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(1500);
        factory.setReadTimeout(2500);
        this.restTemplate = new RestTemplate(factory);
        this.base = base;
    }

    @SuppressWarnings("unchecked")
    public Map<Long, Integer> completedMerchantCounts(long userId) {
        try {
            Map<String, Object> body = restTemplate.getForObject(
                    base + "/internal/users/{id}/completed-merchants", Map.class, userId);
            if (body == null) {
                return Map.of();
            }
            Object data = body.get("data");
            if (!(data instanceof Map<?, ?> map)) {
                return Map.of();
            }
            Object counts = map.get("counts");
            if (!(counts instanceof Map<?, ?> raw)) {
                return Map.of();
            }
            Map<Long, Integer> out = new HashMap<>();
            raw.forEach((k, v) -> {
                try {
                    out.put(Long.parseLong(String.valueOf(k)), ((Number) v).intValue());
                } catch (Exception ignored) {
                    // 跳过坏行
                }
            });
            return out;
        } catch (Exception ex) {
            log.debug("完成单亲和不可用: {}", ex.getMessage());
            return Map.of();
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> riderDailyIncome(long riderId, int days) {
        try {
            Map<String, Object> body = restTemplate.getForObject(
                    base + "/internal/riders/{id}/daily-income?days={days}", Map.class, riderId, days);
            if (body == null) {
                return Map.of();
            }
            Object data = body.get("data");
            if (data instanceof Map<?, ?> map) {
                Map<String, Object> out = new HashMap<>();
                map.forEach((k, v) -> out.put(String.valueOf(k), v));
                return out;
            }
            return Map.of();
        } catch (Exception ex) {
            log.debug("骑手日收入不可用: {}", ex.getMessage());
            return Map.of();
        }
    }
}

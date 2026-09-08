package com.shansuda.activity.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.core.io.ClassPathResource;

import java.util.Arrays;
import java.util.List;

public class RedisLuaAtomicStock implements AtomicStock {

    private final StringRedisTemplate redis;
    private final DefaultRedisScript<List> script;

    public RedisLuaAtomicStock(StringRedisTemplate redis) {
        this.redis = redis;
        this.script = new DefaultRedisScript<>();
        this.script.setLocation(new ClassPathResource("lua/stock_deduct.lua"));
        this.script.setResultType(List.class);
    }

    @Override
    @SuppressWarnings("unchecked")
    public StockResult deduct(String stockKey, String idemKey, String payload, long ttlSeconds) {
        List<Object> raw = redis.execute(script, Arrays.asList(stockKey, idemKey),
                String.valueOf(ttlSeconds), payload);
        if (raw == null || raw.isEmpty()) {
            return new StockResult(0, "");
        }
        int code = ((Number) raw.get(0)).intValue();
        String stored = raw.size() > 1 && raw.get(1) != null ? String.valueOf(raw.get(1)) : "";
        return new StockResult(code, stored);
    }

    @Override
    public void initStock(String stockKey, int stock) {
        redis.opsForValue().setIfAbsent(stockKey, String.valueOf(stock));
    }

    @Override
    public int remaining(String stockKey) {
        String value = redis.opsForValue().get(stockKey);
        if (value == null || value.isBlank()) {
            return -1;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return -1;
        }
    }
}

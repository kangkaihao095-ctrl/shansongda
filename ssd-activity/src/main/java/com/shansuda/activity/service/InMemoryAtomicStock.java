package com.shansuda.activity.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 与 Lua 同序：先幂等、再读库存、再扣减、再写幂等。供单测与 dry-run。 */
public class InMemoryAtomicStock implements AtomicStock {

    private final Map<String, Integer> stocks = new ConcurrentHashMap<>();
    private final Map<String, String> idem = new ConcurrentHashMap<>();
    private final Object lock = new Object();

    @Override
    public StockResult deduct(String stockKey, String idemKey, String payload, long ttlSeconds) {
        synchronized (lock) {
            String existing = idem.get(idemKey);
            if (existing != null) {
                return new StockResult(-1, existing);
            }
            int stock = stocks.getOrDefault(stockKey, 0);
            if (stock <= 0) {
                return new StockResult(0, "");
            }
            stocks.put(stockKey, stock - 1);
            idem.put(idemKey, payload);
            return new StockResult(1, payload);
        }
    }

    @Override
    public void initStock(String stockKey, int stock) {
        synchronized (lock) {
            stocks.putIfAbsent(stockKey, stock);
        }
    }

    @Override
    public int remaining(String stockKey) {
        return stocks.getOrDefault(stockKey, 0);
    }
}

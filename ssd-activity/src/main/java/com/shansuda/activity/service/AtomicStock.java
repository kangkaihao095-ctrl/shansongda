package com.shansuda.activity.service;

public interface AtomicStock {
    StockResult deduct(String stockKey, String idemKey, String payload, long ttlSeconds);

    void initStock(String stockKey, int stock);

    int remaining(String stockKey);
}

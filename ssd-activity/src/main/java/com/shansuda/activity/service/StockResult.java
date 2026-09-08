package com.shansuda.activity.service;

/** 原子扣减：1 成功、0 库存不足、-1 重复请求。 */
public record StockResult(int code, String payload) {
    public boolean success() { return code == 1; }
    public boolean empty() { return code == 0; }
    public boolean replay() { return code == -1; }
}

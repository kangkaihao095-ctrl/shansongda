package com.shansuda.order.fulfill;

import com.shansuda.order.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Component
public class MerchantAcceptTimeoutJob {

    private static final Logger log = LoggerFactory.getLogger(MerchantAcceptTimeoutJob.class);

    private final OrderService orderService;
    private final Clock clock;
    private final int timeoutMin;

    public MerchantAcceptTimeoutJob(OrderService orderService, Clock clock,
                                    @Value("${ssd.order.merchant-accept-timeout-min:15}") int timeoutMin) {
        this.orderService = orderService;
        this.clock = clock;
        this.timeoutMin = timeoutMin <= 0 ? MerchantAcceptPolicy.DEFAULT_TIMEOUT_MIN : timeoutMin;
    }

    @Scheduled(fixedDelayString = "${ssd.order.merchant-accept-scan-ms:30000}")
    public void scan() {
        Instant now = clock.instant();
        Duration timeout = MerchantAcceptPolicy.timeout(timeoutMin);
        try {
            int n = orderService.handleMerchantAcceptTimeouts(now, timeout);
            if (n > 0) {
                log.info("商家出餐超时处理 {} 单（{} 分钟）", n, timeoutMin);
            }
        } catch (Exception ex) {
            log.warn("商家出餐超时扫描失败: {}", ex.getMessage());
        }
    }
}

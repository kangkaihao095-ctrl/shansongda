package com.shansuda.order.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

@Component
@Order(1)
public class OrderSchemaPatcher implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(OrderSchemaPatcher.class);
    private static final String[] COLUMNS = {
            "pay_channel VARCHAR(20) NULL",
            "pay_status VARCHAR(20) NULL",
            "paid_at TIMESTAMP NULL",
            "pay_amount_cents INT NULL",
            "cancel_reason VARCHAR(255) NULL",
            "refund_reason VARCHAR(255) NULL",
            "refund_reject_reason VARCHAR(255) NULL",
            "resume_status VARCHAR(32) NULL"
    };

    @Override
    public void run(ApplicationArguments args) {
        String user = "shansuda";
        String pass = "shansuda";
        String extra = "useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=UTF-8&connectionCollation=utf8mb4_unicode_ci";
        for (int d = 0; d < 4; d++) {
            String url = "jdbc:mysql://127.0.0.1:3316/ssd_order_" + d + "?" + extra;
            try (Connection c = DriverManager.getConnection(url, user, pass); Statement s = c.createStatement()) {
                for (int t = 0; t < 8; t++) {
                    for (String col : COLUMNS) {
                        String sql = "ALTER TABLE t_order_" + t + " ADD COLUMN " + col;
                        try {
                            s.execute(sql);
                        } catch (Exception ex) {
                            String msg = ex.getMessage() == null ? "" : ex.getMessage();
                            if (!msg.contains("Duplicate column")) {
                                log.warn("补列跳过: {} / {}", sql, msg);
                            }
                        }
                    }
                }
            } catch (Exception ex) {
                log.warn("订单分片补列失败 ds{}: {}", d, ex.getMessage());
            }
        }
    }
}

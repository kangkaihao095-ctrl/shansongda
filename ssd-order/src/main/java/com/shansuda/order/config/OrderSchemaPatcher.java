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
            "resume_status VARCHAR(32) NULL",
            "expect_deliver_at TIMESTAMP NULL",
            "rider_issue_code VARCHAR(32) NULL",
            "rider_issue_text VARCHAR(255) NULL"
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
                    tryAddIndex(s, "t_order_" + t, "idx_order_user_status_id", "(user_id, status, id)");
                    tryAddIndex(s, "t_order_" + t, "idx_order_merchant_status_id", "(merchant_id, status, id)");
                    tryAddIndex(s, "t_order_" + t, "idx_order_rider_status_id", "(rider_id, status, id)");
                }
                if (d == 0) {
                    try {
                        s.execute("""
                                CREATE TABLE IF NOT EXISTS merchant_stat_day (
                                  merchant_id BIGINT NOT NULL,
                                  day DATE NOT NULL,
                                  orders INT NOT NULL DEFAULT 0,
                                  gmv BIGINT NOT NULL DEFAULT 0,
                                  completed INT NOT NULL DEFAULT 0,
                                  refund BIGINT NOT NULL DEFAULT 0,
                                  PRIMARY KEY (merchant_id, day)
                                )
                                """);
                    } catch (Exception ex) {
                        log.warn("创建 merchant_stat_day 失败: {}", ex.getMessage());
                    }
                }
            } catch (Exception ex) {
                log.warn("订单分片补列失败 ds{}: {}", d, ex.getMessage());
            }
        }
    }

    private void tryAddIndex(Statement s, String table, String name, String cols) {
        try {
            s.execute("CREATE INDEX " + name + " ON " + table + " " + cols);
        } catch (Exception ex) {
            String msg = ex.getMessage() == null ? "" : ex.getMessage();
            if (!msg.contains("Duplicate") && !msg.contains("exists")) {
                log.warn("补索引跳过: {} / {}", name, msg);
            }
        }
    }
}

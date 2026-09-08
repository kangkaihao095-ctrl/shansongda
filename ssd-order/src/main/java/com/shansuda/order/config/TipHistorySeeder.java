package com.shansuda.order.config;

import com.shansuda.order.client.AccountClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

/** 从历史完成单抽几单已打赏；幂等靠 order_tip.order_id 唯一键。 */
@Component
@Order(3)
public class TipHistorySeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(TipHistorySeeder.class);
    private static final String[] GIFTS = {"WATER", "MILKTEA", "GIFT", "WATER", "CHICKEN"};

    private final JdbcTemplate jdbc;
    private final AccountClient accountClient;

    public TipHistorySeeder(DataSource dataSource, AccountClient accountClient) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.accountClient = accountClient;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            seed();
        } catch (Exception ex) {
            log.warn("历史打赏回填失败: {}", ex.getMessage());
        }
    }

    private void seed() {
        List<Long> ids = jdbc.query(
                "SELECT id FROM t_order WHERE rider_id = 2 AND user_id = 1 AND status = 'COMPLETED' ORDER BY id ASC",
                (rs, i) -> rs.getLong(1));
        int n = 0;
        for (int i = 0; i < ids.size() && n < GIFTS.length; i++) {
            if (i % 7 != 0) {
                continue;
            }
            long orderId = ids.get(i);
            try {
                accountClient.recordTip(Map.of(
                        "orderId", orderId,
                        "riderId", 2L,
                        "userId", 1L,
                        "giftCode", GIFTS[n]
                ));
                n++;
            } catch (Exception ex) {
                String msg = ex.getMessage() == null ? "" : ex.getMessage();
                if (msg.contains("409") || msg.contains("TIPPED")) {
                    n++;
                    continue;
                }
                log.warn("历史打赏跳过 {}: {}", orderId, msg);
            }
        }
        log.info("历史完成单已打赏 {} 笔", n);
    }
}

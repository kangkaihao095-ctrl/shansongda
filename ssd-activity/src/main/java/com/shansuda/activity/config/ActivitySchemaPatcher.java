package com.shansuda.activity.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

@Component
@Order(1)
public class ActivitySchemaPatcher implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ActivitySchemaPatcher.class);

    private final DataSource dataSource;

    public ActivitySchemaPatcher(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) {
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.execute("""
                    CREATE TABLE IF NOT EXISTS coupon_idem (
                      idem_key VARCHAR(128) PRIMARY KEY,
                      activity_id BIGINT NULL,
                      user_id BIGINT NULL,
                      payload JSON NOT NULL,
                      created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
        } catch (Exception ex) {
            log.warn("补齐 coupon_idem 失败: {}", ex.getMessage());
        }
    }
}

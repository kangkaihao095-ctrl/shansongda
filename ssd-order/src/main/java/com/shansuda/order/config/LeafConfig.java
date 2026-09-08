package com.shansuda.order.config;

import com.shansuda.order.leaf.LeafAllocator;
import com.shansuda.order.strategy.FreightSelector;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class LeafConfig {

    @Bean
    public LeafAllocator leafAllocator(
            @Value("${ssd.leaf.url}") String url,
            @Value("${ssd.leaf.username}") String username,
            @Value("${ssd.leaf.password}") String password) {
        // 号段库单独建池，不注册成 DataSource Bean，避免抢走分片主库
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(url);
        ds.setUsername(username);
        ds.setPassword(password);
        ds.setMaximumPoolSize(4);
        ds.setConnectionTimeout(8000);
        return new LeafAllocator(new JdbcTemplate(ds), "order_id");
    }

    @Bean
    public FreightSelector freightSelector() {
        return new FreightSelector();
    }
}

package com.shansuda.order.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class OrderClockConfig {

    @Bean
    public Clock orderClock() {
        return Clock.system(ZoneId.of("Asia/Shanghai"));
    }
}

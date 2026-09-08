package com.shansuda.activity.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.shansuda.activity.service.AtomicStock;
import com.shansuda.activity.service.InMemoryAtomicStock;
import com.shansuda.activity.service.RedisLuaAtomicStock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

@Configuration
public class ActivityConfig {

    @Bean
    public Cache<String, Object> activityLocalCache() {
        return Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(10))
                .maximumSize(10_000)
                .build();
    }

    @Bean
    public AtomicStock atomicStock(
            @Value("${ssd.mode:auto}") String mode,
            StringRedisTemplate redis) {
        if ("dry-run".equals(mode)) {
            return new InMemoryAtomicStock();
        }
        return new RedisLuaAtomicStock(redis);
    }
}

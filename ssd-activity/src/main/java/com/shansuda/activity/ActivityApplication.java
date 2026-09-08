package com.shansuda.activity;

import com.shansuda.activity.service.ActivityService;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;

@SpringBootApplication(scanBasePackages = {"com.shansuda.activity", "com.shansuda.common"})
@EnableFeignClients
public class ActivityApplication {
    public static void main(String[] args) {
        SpringApplication.run(ActivityApplication.class, args);
    }

    @Bean
    ApplicationRunner warmStock(ActivityService activityService) {
        return args -> activityService.warmStock();
    }
}

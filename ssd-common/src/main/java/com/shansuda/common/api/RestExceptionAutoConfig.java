package com.shansuda.common.api;

import org.springframework.context.annotation.Configuration;

@Configuration
public class RestExceptionAutoConfig {
    // 触发 RestExceptionHandler 组件扫描配套；handler 本身带 @RestControllerAdvice
}

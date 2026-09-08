package com.shansuda.common.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JwtAutoConfig {

    @Bean
    public JwtService jwtService(
            @Value("${ssd.jwt.secret:change-me-in-prod-please-32chars}") String secret,
            @Value("${ssd.jwt.ttl-seconds:86400}") long ttlSeconds) {
        return new JwtService(secret, ttlSeconds);
    }

    @Bean
    public FilterRegistrationBean<JwtAuthFilter> jwtAuthFilter(JwtService jwtService) {
        FilterRegistrationBean<JwtAuthFilter> bean = new FilterRegistrationBean<>(new JwtAuthFilter(jwtService));
        bean.setOrder(1);
        bean.addUrlPatterns("/api/*");
        return bean;
    }

    @Bean
    public FilterRegistrationBean<InternalAuthFilter> internalAuthFilter(
            @Value("${ssd.internal.token:ssd-internal-local}") String token) {
        FilterRegistrationBean<InternalAuthFilter> bean =
                new FilterRegistrationBean<>(new InternalAuthFilter(token));
        bean.setOrder(0);
        bean.addUrlPatterns("/internal/*");
        return bean;
    }
}

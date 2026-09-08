package com.shansuda.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shansuda.common.api.ApiResult;
import com.shansuda.common.auth.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Configuration
public class GatewayJwtConfig {

    @Bean
    public JwtService jwtService(
            @Value("${ssd.jwt.secret:change-me-in-prod-please-32chars}") String secret,
            @Value("${ssd.jwt.ttl-seconds:86400}") long ttlSeconds) {
        return new JwtService(secret, ttlSeconds);
    }

    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOriginPatterns(List.of("*"));
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("*"));
        cors.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        return new CorsWebFilter(source);
    }

    @Bean
    public WebFilter jwtWebFilter(JwtService jwtService, ObjectMapper objectMapper) {
        return (ServerWebExchange exchange, WebFilterChain chain) -> {
            if (exchange.getRequest().getMethod() == HttpMethod.OPTIONS) {
                return chain.filter(exchange);
            }
            String path = exchange.getRequest().getURI().getPath();
            if (path.startsWith("/api/auth/") || path.startsWith("/api/avatars")
                    || (path.startsWith("/api/review-photos") && exchange.getRequest().getMethod() == HttpMethod.GET)
                    || path.startsWith("/api/map/") || path.startsWith("/actuator")) {
                return chain.filter(exchange);
            }
            if (!path.startsWith("/api/")) {
                return chain.filter(exchange);
            }
            String header = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            if (header == null || !header.startsWith("Bearer ")) {
                return unauthorized(exchange.getResponse(), objectMapper, "缺少 Token");
            }
            try {
                jwtService.parse(header.substring(7));
                return chain.filter(exchange);
            } catch (Exception ex) {
                return unauthorized(exchange.getResponse(), objectMapper, "Token 无效");
            }
        };
    }

    private Mono<Void> unauthorized(ServerHttpResponse response, ObjectMapper mapper, String message) {
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        try {
            byte[] bytes = mapper.writeValueAsBytes(ApiResult.fail("UNAUTHORIZED", message));
            DataBuffer buffer = response.bufferFactory().wrap(bytes);
            return response.writeWith(Mono.just(buffer));
        } catch (Exception ex) {
            byte[] bytes = "{\"ok\":false,\"code\":\"UNAUTHORIZED\"}".getBytes(StandardCharsets.UTF_8);
            return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
        }
    }
}

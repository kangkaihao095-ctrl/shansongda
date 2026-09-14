package com.shansuda.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/**
 * 下游超时 / 连接失败映射 502，错误体 {@code {code:UPSTREAM}}。不做重试。
 */
@Component
@Order(-1)
public class UpstreamErrorHandler implements ErrorWebExceptionHandler {

    private final ObjectMapper objectMapper;

    public UpstreamErrorHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        if (!isUpstream(ex)) {
            return Mono.error(ex);
        }
        ServerHttpResponse response = exchange.getResponse();
        if (response.isCommitted()) {
            return Mono.error(ex);
        }
        response.setStatusCode(HttpStatus.BAD_GATEWAY);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", false);
        body.put("code", "UPSTREAM");
        body.put("message", "下游超时或连接失败");
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(body);
            DataBuffer buffer = response.bufferFactory().wrap(bytes);
            return response.writeWith(Mono.just(buffer));
        } catch (Exception writeEx) {
            byte[] bytes = "{\"ok\":false,\"code\":\"UPSTREAM\"}".getBytes(StandardCharsets.UTF_8);
            return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
        }
    }

    static boolean isUpstream(Throwable ex) {
        Throwable cur = ex;
        while (cur != null) {
            if (cur instanceof ConnectException || cur instanceof SocketTimeoutException
                    || cur instanceof TimeoutException) {
                return true;
            }
            if (cur instanceof ResponseStatusException rse) {
                HttpStatus status = HttpStatus.resolve(rse.getStatusCode().value());
                if (status == HttpStatus.GATEWAY_TIMEOUT || status == HttpStatus.BAD_GATEWAY
                        || status == HttpStatus.SERVICE_UNAVAILABLE) {
                    return true;
                }
            }
            String name = cur.getClass().getName();
            if (name.contains("ConnectException") || name.contains("TimeoutException")
                    || name.contains("PrematureClose") || name.contains("WebClientRequestException")) {
                return true;
            }
            String msg = cur.getMessage() == null ? "" : cur.getMessage().toLowerCase();
            if (msg.contains("connection refused") || msg.contains("connection reset")
                    || msg.contains("timed out") || msg.contains("timeout")
                    || msg.contains("failed to resolve") || msg.contains("connection prematurely closed")) {
                return true;
            }
            cur = cur.getCause();
        }
        return false;
    }
}

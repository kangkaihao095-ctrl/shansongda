package com.shansuda.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.RouteToRequestUrlFilter;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Duration;

/**
 * nearby 默认走 account MySQL bbox。仅当配置了 {@code ssd.search.uri} 且健康检查通过时才切 18085。
 * 本机不要为了切流量去 docker start ES。
 */
@Component
public class NearbySearchFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(NearbySearchFilter.class);
    private static final Duration CHECK_TTL = Duration.ofSeconds(10);
    private static final Duration PING_TIMEOUT = Duration.ofMillis(400);

    private final String searchUri;
    private final WebClient webClient;
    private volatile long lastCheckMs;
    private volatile boolean healthy;

    public NearbySearchFilter(@Value("${ssd.search.uri:}") String searchUri) {
        this.searchUri = searchUri == null ? "" : searchUri.trim();
        this.webClient = WebClient.builder().build();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (searchUri.isBlank()) {
            return chain.filter(exchange);
        }
        String path = exchange.getRequest().getURI().getPath();
        if (!"/api/riders/nearby".equals(path)) {
            return chain.filter(exchange);
        }
        return searchHealthy().flatMap(ok -> {
            if (!ok) {
                return chain.filter(exchange);
            }
            URI target = UriComponentsBuilder.fromUriString(searchUri)
                    .replacePath(path)
                    .query(exchange.getRequest().getURI().getRawQuery())
                    .build(true)
                    .toUri();
            exchange.getAttributes().put(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR, target);
            return chain.filter(exchange);
        });
    }

    private Mono<Boolean> searchHealthy() {
        long now = System.currentTimeMillis();
        if (now - lastCheckMs < CHECK_TTL.toMillis()) {
            return Mono.just(healthy);
        }
        String healthUrl = searchUri.endsWith("/") ? searchUri + "actuator/health" : searchUri + "/actuator/health";
        return webClient.get()
                .uri(healthUrl)
                .retrieve()
                .toBodilessEntity()
                .timeout(PING_TIMEOUT)
                .map(res -> {
                    HttpStatusCode status = res.getStatusCode();
                    boolean ok = status != null && status.is2xxSuccessful();
                    healthy = ok;
                    lastCheckMs = System.currentTimeMillis();
                    if (ok) {
                        log.info("nearby 上游切 search {}", searchUri);
                    }
                    return ok;
                })
                .onErrorResume(ex -> {
                    healthy = false;
                    lastCheckMs = System.currentTimeMillis();
                    log.debug("search 不健康，nearby 仍走 account: {}", ex.getMessage());
                    return Mono.just(false);
                });
    }

    @Override
    public int getOrder() {
        return RouteToRequestUrlFilter.ROUTE_TO_URL_FILTER_ORDER + 1;
    }
}

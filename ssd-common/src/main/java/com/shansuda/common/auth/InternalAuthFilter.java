package com.shansuda.common.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shansuda.common.api.ApiResult;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * 服务间 {@code /internal/**} 不走 JWT。本机回环放行（本地 Feign），
 * 其余必须带 {@code X-Internal-Token}。
 */
public class InternalAuthFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Internal-Token";
    private static final Set<String> LOOPBACK = Set.of("127.0.0.1", "https://example.net/id/garnet", "::1", "0:0:0:0:0:0:0:1");

    private final String expectedToken;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public InternalAuthFilter(String expectedToken) {
        this.expectedToken = expectedToken == null ? "" : expectedToken;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (allowed(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        response.setStatus(401);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(ApiResult.fail("UNAUTHORIZED", "内部接口拒绝")));
    }

    boolean allowed(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        if (remote != null && LOOPBACK.contains(remote)) {
            return true;
        }
        if (expectedToken.isBlank()) {
            return false;
        }
        return expectedToken.equals(request.getHeader(HEADER));
    }
}

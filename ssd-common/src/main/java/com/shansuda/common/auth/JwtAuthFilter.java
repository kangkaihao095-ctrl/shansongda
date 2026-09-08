package com.shansuda.common.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shansuda.common.api.ApiResult;
import com.shansuda.common.api.BizException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public JwtAuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/api/auth/")
                || path.startsWith("/api/avatars")
                || (path.startsWith("/api/review-photos") && "GET".equalsIgnoreCase(request.getMethod()))
                || path.startsWith("/api/map/")
                || path.startsWith("/actuator")
                || path.startsWith("/internal/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            write(response, 401, "UNAUTHORIZED", "缺少 Token");
            return;
        }
        try {
            AuthHolder.set(jwtService.parse(header.substring(7)));
        } catch (BizException ex) {
            write(response, ex.httpStatus(), ex.code(), ex.getMessage());
            return;
        } catch (Exception ex) {
            write(response, 401, "UNAUTHORIZED", "Token 无效");
            return;
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            AuthHolder.clear();
        }
    }

    private void write(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(ApiResult.fail(code, message)));
    }
}

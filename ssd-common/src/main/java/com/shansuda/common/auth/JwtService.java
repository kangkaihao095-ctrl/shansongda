package com.shansuda.common.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);
    /** 仅本地演示默认值，生产必须通过 SSD_JWT_SECRET 覆盖，不要把生产密钥写进仓库。 */
    public static final String DEFAULT_SECRET = "change-me-in-prod-please-32chars";

    private final SecretKey key;
    private final long ttlSeconds;

    public JwtService(String secret, long ttlSeconds) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalArgumentException("SSD_JWT_SECRET 至少 32 字符");
        }
        if (DEFAULT_SECRET.equals(secret)) {
            log.warn("正在使用默认 JWT secret，仅限本地演示；生产必须设置环境变量 SSD_JWT_SECRET（≥32 字符），不要把生产密钥写入仓库");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.ttlSeconds = ttlSeconds;
    }

    public String issue(long userId, String role, Long riderId) {
        Instant now = Instant.now();
        var builder = Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("role", role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(ttlSeconds)));
        if (riderId != null) {
            builder.claim("rid", riderId);
        }
        return builder.signWith(key).compact();
    }

    public AuthUser parse(String token) {
        Claims claims = Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token)
                .getPayload();
        long userId = Long.parseLong(claims.getSubject());
        String role = claims.get("role", String.class);
        Number rid = claims.get("rid", Number.class);
        Long riderId = rid == null ? null : rid.longValue();
        return new AuthUser(userId, role, riderId);
    }
}

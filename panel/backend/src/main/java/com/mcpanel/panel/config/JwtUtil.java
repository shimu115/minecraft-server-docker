package com.mcpanel.panel.config;

import com.mcpanel.panel.common.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;
import java.util.Map;

/**
 * JWT 签发与校验工具。
 */
@Component
public class JwtUtil {

    private final SecretKey key;
    private final long expirationSeconds;

    public JwtUtil(@Value("${app.jwt-secret}") String secret,
                   @Value("${app.jwt-expiration}") long expirationSeconds) {
        byte[] keyBytes = Base64.getDecoder().decode(
                Base64.getEncoder().encodeToString(secret.getBytes()));
        this.key = Keys.hmacShaKeyFor(keyBytes);
        this.expirationSeconds = expirationSeconds;
    }

    /**
     * 签发 JWT，payload 包含 userId、username、role。
     */
    public String generateToken(Long userId, String username, String role) {
        Date now = new Date();
        Date exp = new Date(now.getTime() + expirationSeconds * 1000);

        return Jwts.builder()
                .claims(Map.of("userId", userId, "username", username, "role", role))
                .issuedAt(now)
                .expiration(exp)
                .signWith(key)
                .compact();
    }

    /**
     * 校验并返回 Claims，校验失败返回 null。
     */
    public Claims validateToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 判断 JWT 是否过期。
     */
    public boolean isExpired(Claims claims) {
        return claims.getExpiration().before(new Date());
    }
}

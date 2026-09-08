package it.guowei.healthapp.common.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT工具类（企业版）
 * 支持：用户ID、用户名、用户类型、滑动过期
 */
@Component
public class JwtUtil {

    @Value("${app.jwt.secret:mySecretKey123456789012345678901234}")
    private String secret;

    @Value("${app.jwt.expiration-days:7}")
    private long expirationDays;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(Long userId, String username, Integer userType) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("username", username);
        claims.put("userType", userType);
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationDays * 24 * 3600 * 1000);
        return Jwts.builder()
                // 注意顺序：先 claims() 后 subject()，避免 claims(Map) 覆盖 sub
                .claims(claims)
                // subject=username：与旧版 JwtInterceptor（按 subject 提取用户名）保持兼容
                .subject(username)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(getSigningKey())
                .compact();
    }

    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public Long getUserId(String token) {
        Claims claims = parseToken(token);
        Object userId = claims.get("userId");
        return userId != null ? Long.valueOf(userId.toString()) : null;
    }

    public String getUsername(String token) {
        Claims claims = parseToken(token);
        return claims.get("username", String.class);
    }

    public Integer getUserType(String token) {
        Claims claims = parseToken(token);
        Object userType = claims.get("userType");
        return userType != null ? Integer.valueOf(userType.toString()) : null;
    }

    public boolean isTokenExpired(String token) {
        try {
            Claims claims = parseToken(token);
            return claims.getExpiration().before(new Date());
        } catch (Exception e) {
            return true;
        }
    }
}

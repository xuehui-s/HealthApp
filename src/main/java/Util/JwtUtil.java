package Util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {

    // 密钥（32字节以上，符合HS256要求）
    private static final String SECRET_KEY = "mySecretKey123456789012345678901234";

    // 生成 KEY（使用 Keys.hmacShaKeyFor）
    private static final SecretKey KEY = Keys.hmacShaKeyFor(
            SECRET_KEY.getBytes(StandardCharsets.UTF_8));

    // 过期时间 7 天
    private static final long EXPIRATION = 1000 * 60 * 60 * 24 * 7;

    // ======================
    // 生成 Token
    // ======================
    public static String generateToken(String username) {
        return Jwts.builder()
                .subject(username)
                .expiration(new Date(System.currentTimeMillis() + EXPIRATION))
                .signWith(KEY)
                .compact();
    }

    // ======================
    // 解析 Token
    // ======================
    public static String extractUsername(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(KEY)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return claims.getSubject();
        } catch (Exception e) {
            return null;
        }
    }
}
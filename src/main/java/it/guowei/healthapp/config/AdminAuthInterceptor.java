package it.guowei.healthapp.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.guowei.healthapp.common.context.UserContext;
import it.guowei.healthapp.common.result.Result;
import it.guowei.healthapp.common.result.ResultCode;
import it.guowei.healthapp.common.util.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.concurrent.TimeUnit;

/**
 * 管理端认证拦截器（/api/v1/admin/**）
 * 校验链：Bearer Token → JWT 签名/过期 → Redis 会话一致性（支持注销/顶号）
 * → 角色必须是管理员(userType=3) → 填充 UserContext → 滑动续期
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminAuthInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String TOKEN_PREFIX = "JWT_TOKEN_";
    private static final long SLIDING_EXPIRE_MINUTES = 30;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        if (token == null || token.isBlank()) {
            return reject(response, ResultCode.UNAUTHORIZED);
        }
        try {
            Claims claims = jwtUtil.parseToken(token);
            String username = claims.getSubject();
            Object userTypeObj = claims.get("userType");
            int userType = userTypeObj != null ? Integer.parseInt(userTypeObj.toString()) : -1;

            // Redis 会话校验：token 被注销/顶号后立即失效
            String stored = redisTemplate.opsForValue().get(TOKEN_PREFIX + username);
            if (stored == null || !stored.equals(token)) {
                return reject(response, ResultCode.TOKEN_EXPIRED);
            }
            if (userType != 3) {
                return reject(response, ResultCode.FORBIDDEN);
            }

            Object userIdObj = claims.get("userId");
            Long userId = userIdObj != null ? Long.valueOf(userIdObj.toString()) : null;
            UserContext.set(new UserContext.UserInfo(userId, username, userType));
            UserContext.get().setToken(token);

            // 滑动续期
            redisTemplate.expire(TOKEN_PREFIX + username, SLIDING_EXPIRE_MINUTES, TimeUnit.MINUTES);
            return true;
        } catch (Exception e) {
            log.warn("管理端Token校验失败: {}", e.getMessage());
            return reject(response, ResultCode.TOKEN_INVALID);
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        UserContext.clear();
    }

    private boolean reject(HttpServletResponse response, ResultCode code) throws Exception {
        response.setStatus(200); // 统一由响应体 code 表达（前端按 code 处理）
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(Result.fail(code)));
        return false;
    }
}

package Config;

import Util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class JwtInterceptor implements HandlerInterceptor {

    @Autowired
    private StringRedisTemplate redisTemplate;

    // Token 在 Redis 中的前缀
    private static final String TOKEN_PREFIX = "JWT_TOKEN_";
    
    // 滑动过期时间：30分钟
    private static final long SLIDING_EXPIRE_MINUTES = 30;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 获取请求头中的 token
        String token = request.getHeader("Authorization");
        
        // 如果没有 token，直接放行（由具体接口决定是否需要登录）
        if (token == null || token.isEmpty()) {
            log.debug("请求未携带 Token，放行: {}", request.getRequestURI());
            return true;
        }

        // 去除 "Bearer " 前缀（如果有）
        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }

        // 解析 token 获取用户名
        String username = JwtUtil.extractUsername(token);
        
        if (username == null) {
            log.warn("Token 无效或已过期");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"Token无效或已过期\",\"data\":null}");
            return false;
        }

        // 检查 Redis 中是否存在该 token
        String redisKey = TOKEN_PREFIX + username;
        String storedToken = redisTemplate.opsForValue().get(redisKey);
        
        if (storedToken == null || !storedToken.equals(token)) {
            log.warn("Token 已被注销或不匹配");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"Token已失效，请重新登录\",\"data\":null}");
            return false;
        }

        // ✅ 关键：每次访问都刷新 Redis 过期时间（滑动窗口）
        redisTemplate.expire(redisKey, SLIDING_EXPIRE_MINUTES, TimeUnit.MINUTES);
        log.debug("Token 续期成功，用户: {}, URI: {}", username, request.getRequestURI());

        // 将用户信息存入 request，方便后续使用
        request.setAttribute("username", username);
        
        return true;
    }
}

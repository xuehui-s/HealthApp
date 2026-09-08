package AOP;

import Annotation.RateLimit;
import Dto.Result;
import constant.BusinessCode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

/**
 * Redis 滑动窗口限流切面
 */
@Slf4j
@Aspect
@Component
public class RateLimitAspect {

    @Autowired
    private StringRedisTemplate redisTemplate;

    private static final String RATE_LIMIT_PREFIX = "rate_limit:";

    @Around("@annotation(Annotation.RateLimit)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        RateLimit rateLimit = method.getAnnotation(RateLimit.class);

        // 构建限流key：前缀 + 注解key + IP地址
        String key = RATE_LIMIT_PREFIX + rateLimit.key();
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
        String ip = getClientIp(request);
        String redisKey = key + ":" + ip;

        long limit = rateLimit.limit();
        long window = rateLimit.window();
        TimeUnit timeUnit = rateLimit.timeUnit();

        // 使用 Redis 递增计数器
        Long currentCount = redisTemplate.opsForValue().increment(redisKey, 1);

        if (currentCount != null && currentCount == 1) {
            // 第一次访问，设置过期时间
            redisTemplate.expire(redisKey, window, timeUnit);
        }

        if (currentCount != null && currentCount > limit) {
            log.warn("[限流触发] key: {}, ip: {}, count: {}/{}", key, ip, currentCount, limit);
            return Result.fail(rateLimit.message());
        }

        return joinPoint.proceed();
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}

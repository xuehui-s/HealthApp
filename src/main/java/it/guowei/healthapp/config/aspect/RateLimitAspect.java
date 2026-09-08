package it.guowei.healthapp.config.aspect;

import it.guowei.healthapp.common.annotation.RateLimit;
import it.guowei.healthapp.common.context.UserContext;
import it.guowei.healthapp.common.exception.BusinessException;
import it.guowei.healthapp.common.result.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 限流切面（基于Redisson RateLimiter）
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class RateLimitAspect {

    private final RedissonClient redissonClient;

    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint point, RateLimit rateLimit) throws Throwable {
        String key = buildKey(rateLimit);
        RRateLimiter limiter = redissonClient.getRateLimiter(key);
        limiter.trySetRate(RateType.OVERALL, rateLimit.count(), rateLimit.time(), RateIntervalUnit.SECONDS);

        if (!limiter.tryAcquire()) {
            log.warn("接口限流触发: key={}, count={}/{}s", key, rateLimit.count(), rateLimit.time());
            throw new BusinessException(ResultCode.TOO_MANY_REQUESTS);
        }
        return point.proceed();
    }

    private String buildKey(RateLimit rateLimit) {
        StringBuilder sb = new StringBuilder(rateLimit.key());
        switch (rateLimit.type()) {
            case IP -> sb.append(getClientIp());
            case USER -> sb.append(UserContext.getUserId() != null ? UserContext.getUserId() : "anonymous");
            default -> sb.append("default");
        }
        return sb.toString();
    }

    private String getClientIp() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) return "unknown";
        HttpServletRequest request = attrs.getRequest();
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip != null ? ip.split(",")[0].trim() : "unknown";
    }
}

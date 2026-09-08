package it.guowei.healthapp.config.aspect;

import it.guowei.healthapp.common.annotation.Idempotent;
import it.guowei.healthapp.common.context.UserContext;
import it.guowei.healthapp.common.exception.BusinessException;
import it.guowei.healthapp.common.result.ResultCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 幂等性切面（基于Redis SETNX）
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class IdempotentAspect {

    private final StringRedisTemplate redisTemplate;
    private final RedissonClient redissonClient;
    private static final String KEY_PREFIX = "idempotent:";

    @Around("@annotation(idempotent)")
    public Object around(ProceedingJoinPoint point, Idempotent idempotent) throws Throwable {
        String key = resolveKey(point, idempotent);
        String lockKey = KEY_PREFIX + key;

        Boolean set = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", idempotent.expire(), TimeUnit.SECONDS);
        if (Boolean.FALSE.equals(set)) {
            log.warn("幂等性拦截: key={}", lockKey);
            throw new BusinessException(ResultCode.IDEMPOTENT_REPEAT, idempotent.message());
        }

        try {
            return point.proceed();
        } catch (Throwable e) {
            // 执行失败时释放key，允许重试
            redisTemplate.delete(lockKey);
            throw e;
        }
    }

    private String resolveKey(ProceedingJoinPoint point, Idempotent idempotent) {
        String keyExpr = idempotent.key();
        if (keyExpr == null || keyExpr.isBlank()) {
            // 默认使用方法签名 + 用户ID
            String methodName = point.getSignature().toShortString();
            Long userId = UserContext.getUserId();
            return methodName + ":" + (userId != null ? userId : "anonymous");
        }
        // SpEL解析
        ExpressionParser parser = new SpelExpressionParser();
        StandardEvaluationContext context = new StandardEvaluationContext();
        Object[] args = point.getArgs();
        String[] paramNames = new String[args.length];
        for (int i = 0; i < args.length; i++) {
            paramNames[i] = "p" + i;
            context.setVariable(paramNames[i], args[i]);
        }
        Object value = parser.parseExpression(keyExpr).getValue(context);
        return value != null ? value.toString() : keyExpr;
    }
}

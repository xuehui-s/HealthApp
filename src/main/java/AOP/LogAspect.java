package AOP;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.annotation.*;

// 内置自定义注解，同文件无需新建类
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@interface OpLog {
    String value() default "";
}

@Slf4j
@Aspect
@Component
public class LogAspect {

    private final ObjectMapper mapper = new ObjectMapper();

    // 切点：匹配 @OpLog 注解的方法
    @Pointcut("@annotation(OpLog)")
    public void logPoint() {}

    @Around("logPoint() && @annotation(opLog)")
    public Object around(ProceedingJoinPoint point, OpLog opLog) throws Throwable {
        long start = System.currentTimeMillis();
        String desc = opLog.value();
        String ip = "unknown";
        String uri = "none";

        // 获取请求信息
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            HttpServletRequest request = attrs.getRequest();
            ip = getIp(request);
            uri = request.getRequestURI();
        }

        String className = point.getSignature().getDeclaringType().getSimpleName();
        String methodName = point.getSignature().getName();
        String args = objToJson(point.getArgs());

        log.info("[{}] 开始 | IP:{} | 接口:{} | 类方法:{}.{} | 参数:{}", desc, ip, uri, className, methodName, args);

        Object result;
        try {
            result = point.proceed();
        } catch (Throwable e) {
            log.error("[{}] 异常 | IP:{} | 接口:{} | 类方法:{}.{} | 异常信息:{}",
                    desc, ip, uri, className, methodName, e.getMessage(), e);
            throw e;
        }

        long cost = System.currentTimeMillis() - start;
        String res = objToJson(result);
        log.info("[{}] 结束 | 耗时:{}ms | 返回:{}", desc, cost, res);

        return result;
    }

    // 获取真实IP
    private String getIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            ip = ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    // 对象转JSON
    private String objToJson(Object obj) {
        if (obj == null) return "null";
        try {
            return mapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return obj.toString();
        }
    }
}
package it.guowei.healthapp.config.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.guowei.healthapp.common.annotation.OperationLog;
import it.guowei.healthapp.common.context.UserContext;
import it.guowei.healthapp.config.aspect.OperationLogRecorder;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;

/**
 * 操作日志切面：审计信息同时写日志文件 + operation_log 表（管理端可查）
 * 注意：本类中的 OperationLog 指「注解」；落库实体用全限定名区分。
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class OperationLogAspect {

    private final ObjectMapper objectMapper;
    private final OperationLogRecorder operationLogRecorder;

    @Around("@annotation(operationLog)")
    public Object around(ProceedingJoinPoint point, OperationLog operationLog) throws Throwable {
        long start = System.currentTimeMillis();
        Object result = null;
        Exception error = null;
        try {
            result = point.proceed();
            return result;
        } catch (Exception e) {
            error = e;
            throw e;
        } finally {
            long cost = System.currentTimeMillis() - start;
            saveLog(point, operationLog, result, error, cost);
        }
    }

    /** 组装审计信息并异步落库（写入由 OperationLogRecorder 独立 Bean 完成，@Async 经代理生效） */
    public void saveLog(ProceedingJoinPoint point, OperationLog operationLog,
                        Object result, Exception error, long cost) {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            HttpServletRequest request = attrs != null ? attrs.getRequest() : null;

            it.guowei.healthapp.domain.entity.OperationLog logEntity =
                    new it.guowei.healthapp.domain.entity.OperationLog();
            logEntity.setUserId(UserContext.getUserId());
            logEntity.setUserType(UserContext.getUserType());
            logEntity.setUsername(UserContext.getUsername());
            logEntity.setModule(operationLog.module());
            logEntity.setDescription(operationLog.description());
            logEntity.setOperationType(operationLog.type());
            logEntity.setMethod(point.getSignature().toShortString());
            logEntity.setParams(operationLog.recordParams()
                    ? truncate(objectMapper.writeValueAsString(point.getArgs()), 2000) : null);
            logEntity.setResult(operationLog.recordResult() && result != null
                    ? truncate(objectMapper.writeValueAsString(result), 2000) : null);
            if (request != null) {
                logEntity.setIp(request.getRemoteAddr());
                logEntity.setUri(request.getRequestURI());
            }
            logEntity.setCostTime(cost);
            logEntity.setStatus(error != null ? 1 : 0);
            logEntity.setErrorMsg(error != null ? truncate(error.getMessage(), 1000) : null);
            logEntity.setCreateTime(LocalDateTime.now());

            operationLogRecorder.persist(logEntity);

            log.info("操作日志: {} | {} | 用户={} | 耗时={}ms | {}",
                    operationLog.module(), operationLog.description(),
                    UserContext.getUsername(), cost,
                    error != null ? "失败:" + error.getMessage() : "成功");
        } catch (Exception e) {
            log.error("操作日志记录失败", e);
        }
    }

    private String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}

package it.guowei.healthapp.config.aspect;

import it.guowei.healthapp.domain.entity.OperationLog;
import it.guowei.healthapp.infrastructure.mapper.OperationLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 操作日志落库器：独立 Bean，保证 @Async 经代理生效（同类自调用会绕过代理）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OperationLogRecorder {

    private final OperationLogMapper operationLogMapper;

    @Async("messageExecutor")
    public void persist(OperationLog logEntity) {
        try {
            operationLogMapper.insert(logEntity);
        } catch (Exception e) {
            log.warn("操作日志落库失败: {}", e.getMessage());
        }
    }
}

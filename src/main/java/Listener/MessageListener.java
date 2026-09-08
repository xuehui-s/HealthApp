package Listener;

import Event.MessageEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import constant.RedisKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
public class MessageListener {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public MessageListener(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = new ObjectMapper();
    }

    // ====================== 事务提交后才发消息（绝对安全） ======================
    @Async("messageExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleMessage(MessageEvent event) {
        try {
            // 手动序列化为纯JSON（不含 @class 元数据），确保与 MessageConsumeJob 兼容
            String json = objectMapper.writeValueAsString(event);
            stringRedisTemplate.opsForList().leftPush(RedisKey.MESSAGE_QUEUE, json);
        } catch (Exception e) {
            log.error("消息序列化失败: userId={}, title={}", event.userId, event.title, e);
        }
    }
}
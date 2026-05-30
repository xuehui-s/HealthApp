package Listener;

import Event.MessageEvent;
import constant.RedisKey;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class MessageListener {

    private final RedisTemplate<String, Object> redisTemplate;

    // ====================== 事务提交后才发消息（绝对安全） ======================
    @Async("messageExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleMessage(MessageEvent event) {
        // 推入 Redis 队列
        redisTemplate.opsForList().leftPush(RedisKey.MESSAGE_QUEUE, event);
    }
}
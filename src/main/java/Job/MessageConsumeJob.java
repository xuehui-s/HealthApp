package Job;

import Event.MessageEvent;
import Mapper.MessageMapper;
import PoJo.SysMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import constant.RedisKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class MessageConsumeJob {

    private final StringRedisTemplate redisTemplate;
    private final MessageMapper messageMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ====================== 定时消费 Redis 队列中的消息 ======================
    @Scheduled(fixedRate = 1000)
    public void consumeMessage() {
        List<SysMessage> messageList = new ArrayList<>();

        // 一次拉 50 条（抗并发关键）
        for (int i = 0; i < 50; i++) {
            String json = redisTemplate.opsForList()
                    .rightPop(RedisKey.MESSAGE_QUEUE);

            if (json == null || json.isEmpty()) break;

            try {
                // 将 JSON 字符串反序列化为 MessageEvent
                MessageEvent event = objectMapper.readValue(json, MessageEvent.class);

                SysMessage msg = new SysMessage();
                msg.setUserId(event.userId);
                msg.setUserType(event.userType);
                msg.setTitle(event.title);
                msg.setContent(event.content);
                msg.setMsgType(event.msgType);
                msg.setRelationId(event.relationId);
                msg.setIsRead(0);
                msg.setCreateTime(LocalDateTime.now());

                messageList.add(msg);
            } catch (Exception e) {
                log.error("消息反序列化失败: {}", json, e);
            }
        }

        // 批量插入数据库！！！性能提升 10~50 倍
        if (!messageList.isEmpty()) {
            try {
                messageMapper.insertBatch(messageList);
                log.info("成功消费{}条消息", messageList.size());
            } catch (Exception e) {
                log.error("批量插入消息失败", e);
            }
        }
    }
}

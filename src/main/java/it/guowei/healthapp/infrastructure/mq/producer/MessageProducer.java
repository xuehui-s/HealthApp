package it.guowei.healthapp.infrastructure.mq.producer;

import it.guowei.healthapp.config.RabbitMQConfig;
import it.guowei.healthapp.infrastructure.mq.message.NotificationMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * 消息生产者
 * 负责发送各类业务消息到RabbitMQ
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MessageProducer {

    private final RabbitTemplate rabbitTemplate;

    /**
     * 发送患者通知
     */
    public void sendPatientNotification(Long userId, String title, String content, Integer msgType, Long relationId) {
        NotificationMessage msg = new NotificationMessage(userId, 1, title, content, msgType, relationId);
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_MESSAGE, "message.patient.notify", msg);
        log.info("发送患者通知消息: userId={}, title={}", userId, title);
    }

    /**
     * 发送医生通知
     */
    public void sendDoctorNotification(Long userId, String title, String content, Integer msgType, Long relationId) {
        NotificationMessage msg = new NotificationMessage(userId, 2, title, content, msgType, relationId);
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_MESSAGE, "message.doctor.notify", msg);
        log.info("发送医生通知消息: userId={}, title={}", userId, title);
    }

    /**
     * 发送订单超时检查
     * 说明：真正的延迟作废由 Redisson 延迟队列实现（RedissonDelayQueueConfig），
     * 该消息仅作为 MQ 侧的冗余检查信号；订单号放入 content 传递。
     */
    public void sendOrderTimeoutCheck(String orderNo, Long appointmentId, long delaySeconds) {
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_ORDER,
                RabbitMQConfig.ROUTING_ORDER_TIMEOUT,
                new NotificationMessage(null, 0, "订单超时检查", orderNo, 0, appointmentId));
        log.info("发送订单超时检查: orderNo={}, delay={}s", orderNo, delaySeconds);
    }

    /**
     * 发送AI任务
     */
    public void sendAiTask(String taskId, Long userId, String question, Integer userType) {
        NotificationMessage msg = new NotificationMessage(userId, userType, taskId, question, 0, null);
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_AI, RabbitMQConfig.ROUTING_AI_TASK, msg);
        log.info("发送AI任务: taskId={}, userId={}", taskId, userId);
    }
}

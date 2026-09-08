package it.guowei.healthapp.infrastructure.mq.consumer;

import it.guowei.healthapp.config.RabbitMQConfig;
import it.guowei.healthapp.infrastructure.mq.message.NotificationMessage;
import Service.MessageService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 消息消费者（RabbitMQ）
 * 业务侧通过 MessageProducer 投递通知，这里异步落库 sys_message，
 * 失败 nack 重入队（配合 yml 中的 retry 与死信队列形成可靠消费闭环）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MessageConsumer {

    private final MessageService messageService;

    @RabbitListener(queues = RabbitMQConfig.QUEUE_MESSAGE_PATIENT)
    public void consumePatientMessage(NotificationMessage msg, Channel channel,
                                      @Header(AmqpHeaders.DELIVERY_TAG) long tag) {
        try {
            log.info("消费患者通知: userId={}, title={}", msg.getUserId(), msg.getTitle());
            messageService.sendMessage(msg.getUserId(), msg.getUserType(),
                    msg.getTitle(), msg.getContent(), msg.getMsgType(), msg.getRelationId());
            channel.basicAck(tag, false);
        } catch (Exception e) {
            log.error("患者通知消费失败", e);
            try {
                channel.basicNack(tag, false, true); // 重新入队
            } catch (IOException ex) {
                log.error("消息重入队失败", ex);
            }
        }
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_MESSAGE_DOCTOR)
    public void consumeDoctorMessage(NotificationMessage msg, Channel channel,
                                     @Header(AmqpHeaders.DELIVERY_TAG) long tag) {
        try {
            log.info("消费医生通知: userId={}, title={}", msg.getUserId(), msg.getTitle());
            messageService.sendMessage(msg.getUserId(), msg.getUserType(),
                    msg.getTitle(), msg.getContent(), msg.getMsgType(), msg.getRelationId());
            channel.basicAck(tag, false);
        } catch (Exception e) {
            log.error("医生通知消费失败", e);
            try {
                channel.basicNack(tag, false, true);
            } catch (IOException ex) {
                log.error("消息重入队失败", ex);
            }
        }
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_DEAD_LETTER)
    public void consumeDeadLetter(NotificationMessage msg) {
        log.error("死信队列消息: userId={}, title={}, content={}",
                msg.getUserId(), msg.getTitle(), msg.getContent());
        // TODO 接入告警系统（邮件/钉钉）
    }
}

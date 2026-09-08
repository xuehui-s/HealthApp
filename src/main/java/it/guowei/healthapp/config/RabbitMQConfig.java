package it.guowei.healthapp.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 企业级配置
 * 交换机：topic模式，支持路由键灵活匹配
 * 队列：消息通知、订单超时、AI任务、数据同步
 */
@Configuration
public class RabbitMQConfig {

    // ========== 交换机 ==========
    public static final String EXCHANGE_MESSAGE = "healthapp.message.exchange";
    public static final String EXCHANGE_ORDER = "healthapp.order.exchange";
    public static final String EXCHANGE_AI = "healthapp.ai.exchange";
    public static final String EXCHANGE_DEAD_LETTER = "healthapp.deadletter.exchange";

    // ========== 队列 ==========
    public static final String QUEUE_MESSAGE_PATIENT = "healthapp.message.patient";
    public static final String QUEUE_MESSAGE_DOCTOR = "healthapp.message.doctor";
    public static final String QUEUE_ORDER_TIMEOUT = "healthapp.order.timeout";
    public static final String QUEUE_AI_TASK = "healthapp.ai.task";
    public static final String QUEUE_AI_RESULT = "healthapp.ai.result";
    public static final String QUEUE_DEAD_LETTER = "healthapp.deadletter.queue";

    // ========== 路由键 ==========
    public static final String ROUTING_MESSAGE_PATIENT = "message.patient.#";
    public static final String ROUTING_MESSAGE_DOCTOR = "message.doctor.#";
    public static final String ROUTING_ORDER_TIMEOUT = "order.timeout";
    public static final String ROUTING_AI_TASK = "ai.task";
    public static final String ROUTING_AI_RESULT = "ai.result";

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory factory, MessageConverter converter) {
        RabbitTemplate template = new RabbitTemplate(factory);
        template.setMessageConverter(converter);
        // 开启消息确认（生产者确认）
        template.setConfirmCallback((correlationData, ack, cause) -> {
            if (!ack) {
                // 消息未到达交换机，记录日志或重试
            }
        });
        // 开启消息返回（路由失败回调）
        template.setReturnsCallback(returned -> {
            // 消息未路由到队列，转入死信队列
        });
        return template;
    }

    // ========== 交换机声明 ==========
    @Bean
    public TopicExchange messageExchange() {
        return ExchangeBuilder.topicExchange(EXCHANGE_MESSAGE).durable(true).build();
    }

    @Bean
    public TopicExchange orderExchange() {
        return ExchangeBuilder.topicExchange(EXCHANGE_ORDER).durable(true).build();
    }

    @Bean
    public TopicExchange aiExchange() {
        return ExchangeBuilder.topicExchange(EXCHANGE_AI).durable(true).build();
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return ExchangeBuilder.directExchange(EXCHANGE_DEAD_LETTER).durable(true).build();
    }

    // ========== 队列声明（带死信） ==========
    @Bean
    public Queue messagePatientQueue() {
        return QueueBuilder.durable(QUEUE_MESSAGE_PATIENT)
                .withArgument("x-dead-letter-exchange", EXCHANGE_DEAD_LETTER)
                .withArgument("x-dead-letter-routing-key", "deadletter")
                .build();
    }

    @Bean
    public Queue messageDoctorQueue() {
        return QueueBuilder.durable(QUEUE_MESSAGE_DOCTOR)
                .withArgument("x-dead-letter-exchange", EXCHANGE_DEAD_LETTER)
                .withArgument("x-dead-letter-routing-key", "deadletter")
                .build();
    }

    @Bean
    public Queue orderTimeoutQueue() {
        return QueueBuilder.durable(QUEUE_ORDER_TIMEOUT)
                .withArgument("x-dead-letter-exchange", EXCHANGE_DEAD_LETTER)
                .withArgument("x-dead-letter-routing-key", "deadletter")
                .build();
    }

    @Bean
    public Queue aiTaskQueue() {
        return QueueBuilder.durable(QUEUE_AI_TASK)
                .withArgument("x-dead-letter-exchange", EXCHANGE_DEAD_LETTER)
                .withArgument("x-dead-letter-routing-key", "deadletter")
                .build();
    }

    @Bean
    public Queue aiResultQueue() {
        return QueueBuilder.durable(QUEUE_AI_RESULT).build();
    }

    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(QUEUE_DEAD_LETTER).build();
    }

    // ========== 绑定 ==========
    @Bean
    public Binding bindMessagePatient(Queue messagePatientQueue, TopicExchange messageExchange) {
        return BindingBuilder.bind(messagePatientQueue).to(messageExchange).with(ROUTING_MESSAGE_PATIENT);
    }

    @Bean
    public Binding bindMessageDoctor(Queue messageDoctorQueue, TopicExchange messageExchange) {
        return BindingBuilder.bind(messageDoctorQueue).to(messageExchange).with(ROUTING_MESSAGE_DOCTOR);
    }

    @Bean
    public Binding bindOrderTimeout(Queue orderTimeoutQueue, TopicExchange orderExchange) {
        return BindingBuilder.bind(orderTimeoutQueue).to(orderExchange).with(ROUTING_ORDER_TIMEOUT);
    }

    @Bean
    public Binding bindAiTask(Queue aiTaskQueue, TopicExchange aiExchange) {
        return BindingBuilder.bind(aiTaskQueue).to(aiExchange).with(ROUTING_AI_TASK);
    }

    @Bean
    public Binding bindAiResult(Queue aiResultQueue, TopicExchange aiExchange) {
        return BindingBuilder.bind(aiResultQueue).to(aiExchange).with(ROUTING_AI_RESULT);
    }

    @Bean
    public Binding bindDeadLetter(Queue deadLetterQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange).with("deadletter");
    }
}

package Config;

import PoJo.PayOrderDelayMsg;
import PoJo.PayOrder;
import PoJo.Appointment;
import Mapper.AppointmentMapper;
import Mapper.PayOrderMapper;
import constant.AppointmentStatus;
import constant.PayOrderStatus;
import org.redisson.api.RBlockingDeque;
import org.redisson.api.RDelayedQueue;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.Transactional;

import Event.MessageEvent;
import jakarta.annotation.PostConstruct;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class RedissonDelayQueueConfig {

    // 延迟队列名称
    public static final String PAY_ORDER_DELAY_QUEUE = "pay_order_delay_queue";

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private PayOrderMapper payOrderMapper;

    @Autowired
    private AppointmentMapper appointmentMapper;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    private RDelayedQueue<PayOrderDelayMsg> delayedQueue;

    @Bean
    public RDelayedQueue<PayOrderDelayMsg> payOrderDelayQueue() {
        RBlockingDeque<PayOrderDelayMsg> blockingDeque = redissonClient.getBlockingDeque(PAY_ORDER_DELAY_QUEUE);
        delayedQueue = redissonClient.getDelayedQueue(blockingDeque);
        return delayedQueue;
    }

    @PostConstruct
    public void startConsumer() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    PayOrderDelayMsg msg = (PayOrderDelayMsg) redissonClient.getBlockingDeque(PAY_ORDER_DELAY_QUEUE).take();
                    handleExpireOrder(msg);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
    }

    /**
     * 处理超时缴费单
     */
    @Transactional(rollbackFor = Exception.class)
    public void handleExpireOrder(PayOrderDelayMsg msg) {
        String orderNo = msg.getOrderNo();
        Long appointId = msg.getAppointmentId();

        // 1. 查询缴费单获取患者ID
        PayOrder order = payOrderMapper.selectOne(com.baomidou.mybatisplus.core.toolkit.Wrappers.lambdaQuery(PayOrder.class)
                .eq(PayOrder::getOrderNo, orderNo));
        
        if (order == null || order.getStatus() != PayOrderStatus.WAIT_PAY) {
            return; // 订单已处理，无需重复处理
        }

        // 2. 更新缴费单为超时作废
        payOrderMapper.update(null, com.baomidou.mybatisplus.core.toolkit.Wrappers.lambdaUpdate(PayOrder.class)
                .eq(PayOrder::getOrderNo, orderNo)
                .eq(PayOrder::getStatus, PayOrderStatus.WAIT_PAY)
                .set(PayOrder::getStatus, PayOrderStatus.TIME_OUT_INVALID));

        // 3. 更新预约单为就诊终止
        appointmentMapper.update(null, com.baomidou.mybatisplus.core.toolkit.Wrappers.lambdaUpdate(Appointment.class)
                .eq(Appointment::getId, appointId)
                .set(Appointment::getStatus, AppointmentStatus.BILL_EXPIRE));

        // 4. 发送超时通知给患者
        eventPublisher.publishEvent(new MessageEvent(
                this,
                order.getPatientId(),
                1,
                "缴费单已超时",
                "您的缴费单【" + orderNo + "】已超时作废，请重新预约",
                8, // 消息类型：订单超时
                appointId
        ));
    }
}
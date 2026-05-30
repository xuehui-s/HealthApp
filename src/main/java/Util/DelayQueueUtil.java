package Util;

import PoJo.PayOrderDelayMsg;
import org.redisson.api.RDelayedQueue;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;


import java.util.concurrent.TimeUnit;

@Component
public class DelayQueueUtil {

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private RDelayedQueue<PayOrderDelayMsg> payOrderDelayQueue;

    /**
     * 发送缴费单超时消息（默认30分钟）
     */
    public void sendPayOrderDelayMsg(String orderNo, Long appointmentId) {
        PayOrderDelayMsg msg = new PayOrderDelayMsg();
        msg.setOrderNo(orderNo);
        msg.setAppointmentId(appointmentId);
        // 30分钟后超时
        payOrderDelayQueue.offer(msg, 30, TimeUnit.MINUTES);
    }

    /**
     * 移除延迟任务（支付成功时调用）
     */
    public void removePayOrderDelayMsg(String orderNo, Long appointmentId) {
        PayOrderDelayMsg msg = new PayOrderDelayMsg();
        msg.setOrderNo(orderNo);
        msg.setAppointmentId(appointmentId);
        payOrderDelayQueue.remove(msg);
    }
}
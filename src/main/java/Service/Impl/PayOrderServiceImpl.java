package Service.Impl;

import Dto.PayOrderDTO;
import Dto.Result;
import Event.MessageEvent;
import Mapper.AppointmentMapper;
import Mapper.PayOrderMapper;
import PoJo.Appointment;
import PoJo.PayOrder;
import PoJo.PayOrderDelayMsg;
import Service.PayOrderService;
import Util.ApplicationContextUtil;
import Util.SnowflakeIdGenerator;
import cn.hutool.core.date.DateUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import constant.AppointmentStatus;
import constant.PayOrderStatus;
import org.redisson.api.RDelayedQueue;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class PayOrderServiceImpl extends ServiceImpl<PayOrderMapper, PayOrder> implements PayOrderService {

    @Autowired
    private AppointmentMapper appointmentMapper;

    @Autowired
    private SnowflakeIdGenerator snowflake;

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    // ====================== 1. 医生开单 ======================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result createPayOrder(PayOrderDTO dto) {
        Long appointmentId = dto.getAppointmentId();
        BigDecimal amount = dto.getTotalAmount();

        // 1. 查询预约
        Appointment appointment = appointmentMapper.selectById(appointmentId);
        if (appointment == null) {
            return Result.fail("预约单不存在");
        }
        // 2. 只有【已签到待就诊】才能开单
        if (appointment.getStatus() != AppointmentStatus.WAIT_DIAGNOSE) {
            return Result.fail("当前患者不可开单");
        }
        // 3. 防重复开单
        long count = count(Wrappers.lambdaQuery(PayOrder.class)
                .eq(PayOrder::getAppointmentId, appointmentId)
                .eq(PayOrder::getStatus, PayOrderStatus.WAIT_PAY));
        if (count > 0) {
            return Result.fail("已存在待缴费单");
        }
        // 4. 雪花订单号
        String orderNo = String.valueOf(snowflake.nextId());
        // 5. 超时时间 当天23:59:59
        Date expire = DateUtil.endOfDay(new Date());
        // 6. 构建订单
        PayOrder order = new PayOrder();
        order.setOrderNo(orderNo);
        order.setAppointmentId(appointmentId);
        order.setPatientId(Long.valueOf(appointment.getPatientId()));
        order.setDoctorId(Long.valueOf(appointment.getDoctorId()));
        order.setDeptId(Long.valueOf(appointment.getDeptId()));
        order.setTotalAmount(amount);
        order.setStatus(PayOrderStatus.WAIT_PAY);
        order.setExpireTime(expire);
        save(order);
        // 7. 更新预约状态 → 待缴费
        appointmentMapper.update(null, Wrappers.lambdaUpdate(Appointment.class)
                .eq(Appointment::getId, appointmentId)
                .set(Appointment::getStatus, AppointmentStatus.WAIT_PAY));
        // 8. 发消息
        eventPublisher.publishEvent(new MessageEvent(
                this,
                Long.valueOf(appointment.getPatientId()),
                1,
                "已开缴费单",
                "请前往大厅缴费",
                2,
                appointmentId
        ));
        // 在 createPayOrder 方法 return 之前，追加以下代码
// 投递延迟队列：1天后超时（模拟当日24点）
        PayOrderDelayMsg delayMsg = new PayOrderDelayMsg();
        delayMsg.setOrderNo(orderNo);
        delayMsg.setAppointmentId(appointmentId);
// 获取延迟队列 Bean 并投递
        RDelayedQueue<PayOrderDelayMsg> delayQueue = (RDelayedQueue<PayOrderDelayMsg>) ApplicationContextUtil.getBean("payOrderDelayQueue");
        delayQueue.offer(delayMsg, 1, java.util.concurrent.TimeUnit.DAYS);

        return Result.ok("开单成功", orderNo);
    }

    // ====================== 2. 查询患者待缴费 ======================
    @Override
    public Result getWaitPayByPatient(Long patientId) {
        List<PayOrder> list = list(Wrappers.lambdaQuery(PayOrder.class)
                .eq(PayOrder::getPatientId, patientId)
                .eq(PayOrder::getStatus, PayOrderStatus.WAIT_PAY)
                .orderByDesc(PayOrder::getCreateTime));
        return Result.ok(list);
    }

    // ====================== 3. 收费员缴费（核心，已修复锁报错） ======================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result pay(String orderNo, Long payerId) {
        // 1. 查询订单
        PayOrder order = getOne(Wrappers.lambdaQuery(PayOrder.class)
                .eq(PayOrder::getOrderNo, orderNo));
        if (order == null) return Result.fail("订单不存在");

        // 2. 状态判断
        if (order.getStatus() != PayOrderStatus.WAIT_PAY) {
            return Result.fail("订单已处理");
        }

        String lockKey = "order:lock:" + orderNo;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            // 修复：无报错版本
            boolean lockOk = lock.tryLock(0, 10, TimeUnit.SECONDS);
            if (!lockOk) {
                return Result.fail("系统繁忙，请稍后再试");
            }

            // 4. 二次校验
            PayOrder check = getById(order.getId());
            if (check.getStatus() != PayOrderStatus.WAIT_PAY) {
                return Result.fail("订单已支付/已作废");
            }

            // ================== 缴费逻辑 ==================
            order.setStatus(PayOrderStatus.PAY_SUCCESS);
            order.setPayTime(new Date());
            order.setPayerId(payerId);
            updateById(order);

            // 更新预约
            appointmentMapper.update(null, Wrappers.lambdaUpdate(Appointment.class)
                    .eq(Appointment::getId, order.getAppointmentId())
                    .set(Appointment::getStatus, AppointmentStatus.PAY_SUCCESS));

            // 发消息
            eventPublisher.publishEvent(new MessageEvent(
                    this,
                    order.getPatientId(),
                    1,
                    "缴费成功",
                    "已完成缴费，可继续诊疗",
                    3,
                    order.getAppointmentId()
            ));
            // ========== 移除延迟队列，防止超时取消 ==========
            PayOrderDelayMsg delayMsg = new PayOrderDelayMsg();
            delayMsg.setOrderNo(orderNo);
            delayMsg.setAppointmentId(order.getAppointmentId());

            RDelayedQueue<PayOrderDelayMsg> delayQueue =
                    (RDelayedQueue<PayOrderDelayMsg>) ApplicationContextUtil.getBean("payOrderDelayQueue");
            delayQueue.remove(delayMsg);

            return Result.ok("缴费成功");

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Result.fail("操作失败，请重试");
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }

    }


    // ====================== 4. 医生作废单据 ======================
    @Override
    @Transactional
    public Result invalidOrder(String orderNo) {
        PayOrder order = getOne(Wrappers.lambdaQuery(PayOrder.class)
                .eq(PayOrder::getOrderNo, orderNo));
        if (order == null) return Result.fail("订单不存在");

        if (order.getStatus() != PayOrderStatus.WAIT_PAY) {
            return Result.fail("只能作废待缴费单");
        }

        // 作废订单
        order.setStatus(PayOrderStatus.DOCTOR_INVALID);
        updateById(order);

        // 预约 → 就诊终止
        appointmentMapper.update(null, Wrappers.lambdaUpdate(Appointment.class)
                .eq(Appointment::getId, order.getAppointmentId())
                .set(Appointment::getStatus, AppointmentStatus.BILL_EXPIRE));
        PayOrderDelayMsg delayMsg = new PayOrderDelayMsg();
        delayMsg.setOrderNo(orderNo);
        delayMsg.setAppointmentId(order.getAppointmentId());

        RDelayedQueue<PayOrderDelayMsg> delayQueue =
                (RDelayedQueue<PayOrderDelayMsg>) ApplicationContextUtil.getBean("payOrderDelayQueue");
        delayQueue.remove(delayMsg);
// ====================================================
        return Result.ok("已作废");
    }

    // ====================== 5. 我的缴费单 ======================
    @Override
    public Result myList(Long patientId) {
        List<PayOrder> list = list(Wrappers.lambdaQuery(PayOrder.class)
                .eq(PayOrder::getPatientId, patientId)
                .orderByDesc(PayOrder::getCreateTime));

        return Result.ok(list);
    }
}
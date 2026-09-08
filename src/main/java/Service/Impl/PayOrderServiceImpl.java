package Service.Impl;

import Dto.*;
import Event.MessageEvent;
import Exception.BusinessException;
import Mapper.AppointmentMapper;
import Mapper.BillItemMapper;
import Mapper.PayOrderMapper;
import Mapper.RefundOrderMapper;
import PoJo.*;
import Service.PayOrderService;
import Util.ApplicationContextUtil;
import Util.SnowflakeIdGenerator;
import cn.hutool.core.date.DateUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import constant.*;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RDelayedQueue;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class PayOrderServiceImpl extends ServiceImpl<PayOrderMapper, PayOrder> implements PayOrderService {

    @Autowired
    private AppointmentMapper appointmentMapper;

    @Autowired
    private BillItemMapper billItemMapper;

    @Autowired
    private RefundOrderMapper refundOrderMapper;

    @Autowired
    private SnowflakeIdGenerator snowflake;

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    private static final DateTimeFormatter TXN_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    // ====================== 1. 医生开立缴费单（支持费用明细） ======================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result createPayOrder(PayOrderDTO dto) {
        Long appointmentId = dto.getAppointmentId();

        // 1. 查询预约
        Appointment appointment = appointmentMapper.selectById(appointmentId);
        if (appointment == null) {
            throw new BusinessException(BusinessCode.APPOINTMENT_NOT_FOUND);
        }

        // 2. 只有【已签到待就诊】才能开单
        if (appointment.getStatus() != AppointmentStatus.WAIT_DIAGNOSE) {
            throw new BusinessException(BusinessCode.APPOINTMENT_NOT_FOUND, "当前患者状态不可开单");
        }

        // 3. 防重复开单
        long count = count(Wrappers.lambdaQuery(PayOrder.class)
                .eq(PayOrder::getAppointmentId, appointmentId)
                .eq(PayOrder::getStatus, PayOrderStatus.WAIT_PAY));
        if (count > 0) {
            throw new BusinessException(BusinessCode.ORDER_ALREADY_EXISTS);
        }

        // 4. 校验并计算总金额
        BigDecimal calculatedTotal = BigDecimal.ZERO;
        List<BillItem> billItems = new ArrayList<>();

        if (dto.getItems() != null && !dto.getItems().isEmpty()) {
            for (PayOrderDTO.BillItemDTO itemDTO : dto.getItems()) {
                BillItem item = new BillItem();
                item.setCategory(itemDTO.getCategory());
                item.setItemName(itemDTO.getItemName());
                item.setSpecification(itemDTO.getSpecification());
                item.setUnitPrice(itemDTO.getUnitPrice());
                item.setQuantity(itemDTO.getQuantity() != null ? itemDTO.getQuantity() : 1);

                BigDecimal subtotal = itemDTO.getUnitPrice()
                        .multiply(BigDecimal.valueOf(item.getQuantity()));
                item.setSubtotal(subtotal);
                item.setRemark(itemDTO.getRemark());
                billItems.add(item);
                calculatedTotal = calculatedTotal.add(subtotal);
            }
        }

        // 如果前端传了总金额，校验一致；否则使用计算金额
        if (dto.getTotalAmount() != null && dto.getTotalAmount().compareTo(BigDecimal.ZERO) > 0) {
            if (!billItems.isEmpty() && dto.getTotalAmount().compareTo(calculatedTotal) != 0) {
                throw new BusinessException(BusinessCode.PAY_AMOUNT_ERROR,
                        "金额不一致: 传入" + dto.getTotalAmount() + ", 计算" + calculatedTotal);
            }
            calculatedTotal = dto.getTotalAmount();
        }

        if (calculatedTotal.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(BusinessCode.BAD_REQUEST.getCode(), "金额必须大于0");
        }

        // 5. 生成雪花订单号
        String orderNo = String.valueOf(snowflake.nextId());

        // 6. 超时时间：当天23:59:59
        Date expire = DateUtil.endOfDay(new Date());

        // 7. 构建并保存订单
        PayOrder order = new PayOrder();
        order.setOrderNo(orderNo);
        order.setAppointmentId(appointmentId);
        order.setPatientId(Long.valueOf(appointment.getPatientId()));
        order.setDoctorId(Long.valueOf(appointment.getDoctorId()));
        order.setDeptId(Long.valueOf(appointment.getDeptId()));
        order.setTotalAmount(calculatedTotal);
        order.setStatus(PayOrderStatus.WAIT_PAY);
        order.setExpireTime(expire.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDateTime());
        order.setDoctorRemark(dto.getRemark());
        order.setReceiptPrinted(0);
        save(order);

        // 8. 保存费用明细
        if (!billItems.isEmpty()) {
            for (BillItem item : billItems) {
                item.setOrderNo(orderNo);
                item.setCreateTime(LocalDateTime.now());
            }
            billItemMapper.insertBatch(billItems);
        }

        // 9. 更新预约状态 → 待缴费
        appointmentMapper.update(null, Wrappers.lambdaUpdate(Appointment.class)
                .eq(Appointment::getId, appointmentId)
                .set(Appointment::getStatus, AppointmentStatus.WAIT_PAY));

        // 10. 推送消息通知
        eventPublisher.publishEvent(new MessageEvent(
                this,
                Long.valueOf(appointment.getPatientId()),
                1,
                "缴费通知",
                "医生已为您开具缴费单【" + orderNo + "】，金额：¥" + calculatedTotal + "，请前往收费处缴费",
                1,
                appointmentId
        ));

        // 11. 投递延迟队列：超时自动作废
        PayOrderDelayMsg delayMsg = new PayOrderDelayMsg();
        delayMsg.setOrderNo(orderNo);
        delayMsg.setAppointmentId(appointmentId);

        @SuppressWarnings("unchecked")
        RDelayedQueue<PayOrderDelayMsg> delayQueue =
                (RDelayedQueue<PayOrderDelayMsg>) ApplicationContextUtil.getBean("payOrderDelayQueue");
        delayQueue.offer(delayMsg, 1, TimeUnit.DAYS);

        log.info("[创建缴费单] 订单号: {}, 金额: {}, 费用明细: {}项, 患者: {}", orderNo, calculatedTotal, billItems.size(), appointment.getPatientId());

        // 12. 返回详情（含订单号和明细）
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderNo", orderNo);
        result.put("totalAmount", calculatedTotal);
        result.put("itemCount", billItems.size());
        return Result.ok("开单成功", result);
    }

    // ====================== 2. 查询患者待缴费单（含明细） ======================
    @Override
    public Result getWaitPayByPatient(Long patientId) {
        List<PayOrder> list = list(Wrappers.lambdaQuery(PayOrder.class)
                .eq(PayOrder::getPatientId, patientId)
                .eq(PayOrder::getStatus, PayOrderStatus.WAIT_PAY)
                .orderByDesc(PayOrder::getCreateTime));

        // 带明细
        List<Map<String, Object>> resultList = new ArrayList<>();
        for (PayOrder order : list) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("order", order);
            map.put("items", billItemMapper.selectByOrderNo(order.getOrderNo()));
            resultList.add(map);
        }
        return Result.ok(resultList);
    }

    // ====================== 3. 收费员确认缴费（增强版：含支付方式、交易流水号） ======================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result pay(PayRequestDTO request) {
        String orderNo = request.getOrderNo();
        String payMethod = request.getPayMethod();
        Long payerId = request.getPayerId();

        // 1. 校验支付方式
        if (payMethod == null || payMethod.isBlank()) {
            payMethod = PayMethod.CASH; // 默认现金
        }
        if (!Arrays.asList(PayMethod.CASH, PayMethod.WECHAT, PayMethod.ALIPAY,
                PayMethod.BANK_CARD, PayMethod.MEDICARE).contains(payMethod)) {
            throw new BusinessException(BusinessCode.PAY_METHOD_INVALID);
        }

        // 2. 查询订单
        PayOrder order = getOne(Wrappers.lambdaQuery(PayOrder.class)
                .eq(PayOrder::getOrderNo, orderNo));
        if (order == null) {
            throw new BusinessException(BusinessCode.ORDER_NOT_FOUND);
        }

        // 3. 状态校验
        if (order.getStatus() != PayOrderStatus.WAIT_PAY) {
            if (order.getStatus() == PayOrderStatus.PAY_SUCCESS) {
                throw new BusinessException(BusinessCode.ORDER_ALREADY_PAID);
            }
            throw new BusinessException(BusinessCode.ORDER_ALREADY_INVALID);
        }

        // 4. 检查是否过期
        if (order.getExpireTime() != null && order.getExpireTime().isBefore(LocalDateTime.now())) {
            throw new BusinessException(BusinessCode.ORDER_EXPIRED);
        }

        // 5. 分布式锁防并发
        String lockKey = "order:lock:" + orderNo;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            boolean lockOk = lock.tryLock(0, 10, TimeUnit.SECONDS);
            if (!lockOk) {
                return Result.fail("系统繁忙，请稍后再试");
            }

            // 6. 二次校验
            PayOrder check = getById(order.getId());
            if (check.getStatus() != PayOrderStatus.WAIT_PAY) {
                throw new BusinessException(BusinessCode.ORDER_ALREADY_PAID);
            }

            // 7. 生成交易流水号
            String transactionNo = generateTransactionNo(payMethod);

            // 8. 更新订单
            order.setStatus(PayOrderStatus.PAY_SUCCESS);
            order.setPayMethod(payMethod);
            order.setTransactionNo(transactionNo);
            order.setPayTime(LocalDateTime.now());
            order.setPayerId(payerId);
            order.setPayRemark(request.getRemark());
            updateById(order);

            // 9. 更新预约状态
            appointmentMapper.update(null, Wrappers.lambdaUpdate(Appointment.class)
                    .eq(Appointment::getId, order.getAppointmentId())
                    .set(Appointment::getStatus, AppointmentStatus.PAY_SUCCESS));

            // 10. 推送缴费成功消息
            String methodName = PayMethod.getName(payMethod);
            eventPublisher.publishEvent(new MessageEvent(
                    this,
                    order.getPatientId(),
                    1,
                    "缴费成功",
                    "缴费单【" + orderNo + "】已缴费 ¥" + order.getTotalAmount()
                            + "（" + methodName + "），流水号：" + transactionNo,
                    3,
                    order.getAppointmentId()
            ));

            // 11. 移除延迟队列
            removeFromDelayQueue(orderNo, order.getAppointmentId());

            log.info("[缴费成功] 订单号: {}, 金额: {}, 支付方式: {}, 流水号: {}, 收费员: {}",
                    orderNo, order.getTotalAmount(), methodName, transactionNo, payerId);

            // 12. 返回缴费凭证信息
            Map<String, Object> receipt = new LinkedHashMap<>();
            receipt.put("orderNo", orderNo);
            receipt.put("transactionNo", transactionNo);
            receipt.put("amount", order.getTotalAmount());
            receipt.put("payMethod", methodName);
            receipt.put("payTime", order.getPayTime().toString());
            receipt.put("items", billItemMapper.selectByOrderNo(orderNo));
            return Result.ok("缴费成功", receipt);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Result.fail("操作失败，请重试");
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    // ====================== 4. 医生作废缴费单 ======================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result invalidOrder(String orderNo) {
        PayOrder order = getOne(Wrappers.lambdaQuery(PayOrder.class)
                .eq(PayOrder::getOrderNo, orderNo));
        if (order == null) {
            throw new BusinessException(BusinessCode.ORDER_NOT_FOUND);
        }

        if (order.getStatus() != PayOrderStatus.WAIT_PAY) {
            throw new BusinessException(BusinessCode.ORDER_ALREADY_INVALID);
        }

        // 作废订单
        order.setStatus(PayOrderStatus.DOCTOR_INVALID);
        updateById(order);

        // 更新预约状态
        appointmentMapper.update(null, Wrappers.lambdaUpdate(Appointment.class)
                .eq(Appointment::getId, order.getAppointmentId())
                .set(Appointment::getStatus, AppointmentStatus.BILL_EXPIRE));

        // 移除延迟队列
        removeFromDelayQueue(orderNo, order.getAppointmentId());

        // 推送消息
        eventPublisher.publishEvent(new MessageEvent(
                this,
                order.getPatientId(),
                1,
                "缴费单已作废",
                "您的缴费单【" + orderNo + "】已被医生作废，金额：¥" + order.getTotalAmount(),
                8,
                order.getAppointmentId()
        ));

        log.info("[作废缴费单] 订单号: {}, 医生: {}", orderNo, order.getDoctorId());
        return Result.ok("已作废");
    }

    // ====================== 5. 患者我的缴费单（分页） ======================
    @Override
    public Result myList(Long patientId, Integer page, Integer size) {
        int pageNum = (page == null || page < 1) ? 1 : page;
        int pageSize = (size == null || size < 1) ? 20 : Math.min(size, 100);

        Page<PayOrder> pageObj = new Page<>(pageNum, pageSize);
        Page<PayOrder> result = page(pageObj, Wrappers.lambdaQuery(PayOrder.class)
                .eq(PayOrder::getPatientId, patientId)
                .orderByDesc(PayOrder::getCreateTime));

        // 带明细
        List<Map<String, Object>> records = new ArrayList<>();
        for (PayOrder order : result.getRecords()) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("order", order);
            map.put("items", billItemMapper.selectByOrderNo(order.getOrderNo()));
            map.put("statusName", PayOrderStatus.getName(order.getStatus()));
            if (order.getPayMethod() != null) {
                map.put("payMethodName", PayMethod.getName(order.getPayMethod()));
            }
            records.add(map);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("records", records);
        data.put("total", result.getTotal());
        data.put("page", pageNum);
        data.put("size", pageSize);
        return Result.ok(data);
    }

    // ====================== 6. 查询缴费单详情 ======================
    @Override
    public Result getOrderDetail(String orderNo) {
        PayOrder order = getOne(Wrappers.lambdaQuery(PayOrder.class)
                .eq(PayOrder::getOrderNo, orderNo));
        if (order == null) {
            throw new BusinessException(BusinessCode.ORDER_NOT_FOUND);
        }

        List<BillItem> items = billItemMapper.selectByOrderNo(orderNo);

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("order", order);
        detail.put("items", items);
        detail.put("statusName", PayOrderStatus.getName(order.getStatus()));
        if (order.getPayMethod() != null) {
            detail.put("payMethodName", PayMethod.getName(order.getPayMethod()));
        }
        return Result.ok(detail);
    }

    // ====================== 7. 申请退款 ======================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result applyRefund(RefundDTO dto) {
        String orderNo = dto.getOrderNo();
        BigDecimal refundAmount = dto.getRefundAmount();

        PayOrder order = getOne(Wrappers.lambdaQuery(PayOrder.class)
                .eq(PayOrder::getOrderNo, orderNo));
        if (order == null) {
            throw new BusinessException(BusinessCode.ORDER_NOT_FOUND);
        }

        // 只有已缴费和部分退款状态可以退款
        if (order.getStatus() != PayOrderStatus.PAY_SUCCESS
                && order.getStatus() != PayOrderStatus.PARTIAL_REFUND) {
            throw new BusinessException(BusinessCode.REFUND_NOT_ALLOWED);
        }

        // 校验退款金额
        if (refundAmount == null || refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(BusinessCode.BAD_REQUEST.getCode(), "退款金额必须大于0");
        }

        // 计算可退金额（原金额 - 已退金额）
        BigDecimal totalRefunded = getTotalRefundedForOrder(orderNo);
        BigDecimal availableRefund = order.getTotalAmount().subtract(totalRefunded);

        if (refundAmount.compareTo(availableRefund) > 0) {
            throw new BusinessException(BusinessCode.REFUND_AMOUNT_EXCEED,
                    "可退: ¥" + availableRefund + ", 申请: ¥" + refundAmount);
        }

        // 创建退款单
        RefundOrder refund = new RefundOrder();
        refund.setRefundNo("RFN" + snowflake.nextId());
        refund.setOrderNo(orderNo);
        refund.setAppointmentId(order.getAppointmentId());
        refund.setPatientId(order.getPatientId());
        refund.setOriginalAmount(order.getTotalAmount());
        refund.setRefundAmount(refundAmount);
        refund.setRefundMethod(dto.getRefundMethod() != null ? dto.getRefundMethod() : order.getPayMethod());
        refund.setRefundReason(dto.getRefundReason());
        refund.setStatus(0); // 待审核
        refund.setOperatorId(dto.getOperatorId());
        refund.setCreateTime(LocalDateTime.now());
        refundOrderMapper.insert(refund);

        // 更新订单状态为部分退款
        boolean isFullRefund = refundAmount.compareTo(availableRefund) >= 0;
        order.setStatus(isFullRefund ? PayOrderStatus.REFUNDED : PayOrderStatus.PARTIAL_REFUND);
        updateById(order);

        // 推送退款申请消息
        eventPublisher.publishEvent(new MessageEvent(
                this,
                order.getPatientId(),
                1,
                "退款申请已提交",
                "缴费单【" + orderNo + "】退款申请 ¥" + refundAmount + "，退款流水号：" + refund.getRefundNo() + "，请等待审核",
                5,
                order.getAppointmentId()
        ));

        log.info("[退款申请] 退款单号: {}, 原订单: {}, 金额: {}, 全额: {}", refund.getRefundNo(), orderNo, refundAmount, isFullRefund);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("refundNo", refund.getRefundNo());
        result.put("refundAmount", refundAmount);
        result.put("isFullRefund", isFullRefund);
        result.put("status", "待审核");
        return Result.ok("退款申请已提交", result);
    }

    // ====================== 8. 审核退款 ======================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result auditRefund(Long refundId, Integer auditResult, String auditRemark, Long auditorId) {
        RefundOrder refund = refundOrderMapper.selectById(refundId);
        if (refund == null) {
            throw new BusinessException(BusinessCode.NOT_FOUND.getCode(), "退款单不存在");
        }

        if (refund.getStatus() != 0) {
            throw new BusinessException(BusinessCode.CONFLICT.getCode(), "退款单已审核，不可重复操作");
        }

        if (auditResult == 1) {
            // 审核通过
            refund.setStatus(1);
            refund.setAuditorId(auditorId);
            refund.setAuditRemark(auditRemark);
            refund.setAuditTime(LocalDateTime.now());
            refund.setRefundTime(LocalDateTime.now());
            refundOrderMapper.updateById(refund);

            // 推送退款成功消息
            eventPublisher.publishEvent(new MessageEvent(
                    this,
                    refund.getPatientId(),
                    1,
                    "退款已到账",
                    "退款单【" + refund.getRefundNo() + "】已审核通过，退款金额：¥" + refund.getRefundAmount() + "，退款方式：" + PayMethod.getName(refund.getRefundMethod()),
                    5,
                    refund.getAppointmentId()
            ));

            log.info("[退款审核通过] 退款单号: {}, 金额: {}, 审核人: {}", refund.getRefundNo(), refund.getRefundAmount(), auditorId);
            return Result.ok("退款审核通过");
        } else {
            // 审核拒绝
            refund.setStatus(2);
            refund.setAuditorId(auditorId);
            refund.setAuditRemark(auditRemark);
            refund.setAuditTime(LocalDateTime.now());
            refundOrderMapper.updateById(refund);

            // 恢复订单状态
            PayOrder order = getOne(Wrappers.lambdaQuery(PayOrder.class)
                    .eq(PayOrder::getOrderNo, refund.getOrderNo()));
            if (order != null && (order.getStatus() == PayOrderStatus.REFUNDED
                    || order.getStatus() == PayOrderStatus.PARTIAL_REFUND)) {
                order.setStatus(PayOrderStatus.PAY_SUCCESS);
                updateById(order);
            }

            log.info("[退款审核拒绝] 退款单号: {}, 原因: {}, 审核人: {}", refund.getRefundNo(), auditRemark, auditorId);
            return Result.ok("退款审核已拒绝");
        }
    }

    // ====================== 9. 日终结算 ======================
    @Override
    public Result dailySettlement(Long cashierId) {
        LocalDate today = LocalDate.now();

        // 统计营收
        List<Map<String, Object>> stats = baseMapper.dailyRevenueStats(today);
        Long totalOrders = baseMapper.countPaidByDate(today);
        BigDecimal totalRevenue = baseMapper.sumPaidByDate(today);
        BigDecimal refundTotal = refundOrderMapper.sumRefundByDate(today);
        Long refundCount = refundOrderMapper.countRefundByDate(today);

        SettlementDTO settlement = new SettlementDTO();
        settlement.setSettleDate(today);
        settlement.setTotalOrders(totalOrders);
        settlement.setTotalRevenue(totalRevenue != null ? totalRevenue : BigDecimal.ZERO);
        settlement.setRefundCount(refundCount != null ? refundCount : 0L);
        settlement.setRefundTotal(refundTotal != null ? refundTotal : BigDecimal.ZERO);
        settlement.setNetRevenue(settlement.getTotalRevenue().subtract(settlement.getRefundTotal()));
        settlement.setCashierId(cashierId);

        // 按支付方式分组
        for (Map<String, Object> row : stats) {
            String method = String.valueOf(row.get("pay_method"));
            BigDecimal amount = (BigDecimal) row.get("total_amount");
            switch (method) {
                case PayMethod.CASH -> settlement.setCashAmount(amount);
                case PayMethod.WECHAT -> settlement.setWechatAmount(amount);
                case PayMethod.ALIPAY -> settlement.setAlipayAmount(amount);
                case PayMethod.BANK_CARD -> settlement.setBankCardAmount(amount);
                case PayMethod.MEDICARE -> settlement.setMedicareAmount(amount);
            }
        }

        log.info("[日终结算] 日期: {}, 总营收: ¥{}, 退款: ¥{}, 净营收: ¥{}",
                today, settlement.getTotalRevenue(), settlement.getRefundTotal(), settlement.getNetRevenue());

        return Result.ok("结算完成", settlement);
    }

    // ====================== 10. 营收统计 ======================
    @Override
    public Result revenueStats(RevenueStatsDTO dto) {
        List<Map<String, Object>> stats = baseMapper.revenueStats(
                dto.getStartDate(), dto.getEndDate(),
                dto.getDeptId(), dto.getDoctorId(), dto.getPayMethod());

        if (stats == null || stats.isEmpty()) {
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("totalOrders", 0L);
            empty.put("totalRevenue", BigDecimal.ZERO);
            empty.put("avgAmount", BigDecimal.ZERO);
            return Result.ok(empty);
        }

        Map<String, Object> result = stats.get(0);

        // 查询退款数据
        if (dto.getStartDate() != null && dto.getEndDate() != null) {
            // 简化处理：只统计日期范围内的退款
            BigDecimal refundAmount = BigDecimal.ZERO;
            Long refundCount = 0L;
            try {
                refundAmount = refundOrderMapper.sumRefundByDate(LocalDate.now());
                refundCount = refundOrderMapper.countRefundByDate(LocalDate.now());
            } catch (Exception ignored) {}
            result.put("refundAmount", refundAmount);
            result.put("refundCount", refundCount);

            BigDecimal revenue = (BigDecimal) result.getOrDefault("total_revenue", BigDecimal.ZERO);
            result.put("netRevenue", revenue.subtract(refundAmount));
        }

        return Result.ok(result);
    }

    // ====================== 11. 查询退款记录 ======================
    @Override
    public Result refundList(Long patientId) {
        List<RefundOrder> list = refundOrderMapper.selectList(
                Wrappers.lambdaQuery(RefundOrder.class)
                        .eq(RefundOrder::getPatientId, patientId)
                        .orderByDesc(RefundOrder::getCreateTime));
        return Result.ok(list);
    }

    // ====================== 12. 标记收据已打印 ======================
    @Override
    public Result markReceiptPrinted(String orderNo) {
        PayOrder order = getOne(Wrappers.lambdaQuery(PayOrder.class)
                .eq(PayOrder::getOrderNo, orderNo));
        if (order == null) {
            throw new BusinessException(BusinessCode.ORDER_NOT_FOUND);
        }
        order.setReceiptPrinted(1);
        updateById(order);
        return Result.ok("已标记");
    }

    // ====================== 私有辅助方法 ======================
    /**
     * 生成交易流水号
     * 格式: TXN + yyyyMMddHHmmss + 6位随机数
     */
    private String generateTransactionNo(String payMethod) {
        String timestamp = LocalDateTime.now().format(TXN_DATE_FORMAT);
        String random = String.format("%06d", (int) (Math.random() * 1000000));
        return "TXN" + timestamp + random;
    }

    /**
     * 从延迟队列移除
     */
    private void removeFromDelayQueue(String orderNo, Long appointmentId) {
        try {
            PayOrderDelayMsg delayMsg = new PayOrderDelayMsg();
            delayMsg.setOrderNo(orderNo);
            delayMsg.setAppointmentId(appointmentId);
            @SuppressWarnings("unchecked")
            RDelayedQueue<PayOrderDelayMsg> delayQueue =
                    (RDelayedQueue<PayOrderDelayMsg>) ApplicationContextUtil.getBean("payOrderDelayQueue");
            delayQueue.remove(delayMsg);
        } catch (Exception e) {
            log.warn("移除延迟队列失败: orderNo={}, error={}", orderNo, e.getMessage());
        }
    }

    /**
     * 获取订单已退款总金额
     */
    private BigDecimal getTotalRefundedForOrder(String orderNo) {
        List<RefundOrder> refunds = refundOrderMapper.selectList(
                Wrappers.lambdaQuery(RefundOrder.class)
                        .eq(RefundOrder::getOrderNo, orderNo)
                        .eq(RefundOrder::getStatus, 1)); // 只计算已审核通过的
        return refunds.stream()
                .map(RefundOrder::getRefundAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}

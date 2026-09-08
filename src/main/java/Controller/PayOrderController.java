package Controller;

import Dto.*;
import Service.PayOrderService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 缴费系统控制器（企业级完整版）
 * 支持：开单、缴费、退款、结算、营收统计
 */
@RestController
@RequestMapping("/payOrder")
public class PayOrderController {

    @Autowired
    private PayOrderService payOrderService;

    // ==================== 医生端 ====================

    /** 医生开缴费单（支持费用明细） */
    @PostMapping("/create")
    public Result createPayOrder(@Valid @RequestBody PayOrderDTO dto) {
        return payOrderService.createPayOrder(dto);
    }

    /** 医生作废缴费单 */
    @PostMapping("/invalid")
    public Result invalidOrder(@RequestParam String orderNo) {
        return payOrderService.invalidOrder(orderNo);
    }

    // ==================== 收费员端 ====================

    /** 收费员查询患者待缴费单（含费用明细） */
    @GetMapping("/waitPay/{patientId}")
    public Result getWaitPay(@PathVariable Long patientId) {
        return payOrderService.getWaitPayByPatient(patientId);
    }

    /** 收费员确认缴费（支持多种支付方式） */
    @PostMapping("/pay")
    public Result pay(@RequestBody PayRequestDTO request) {
        return payOrderService.pay(request);
    }

    /** 收费员日终结算 */
    @PostMapping("/settlement")
    public Result dailySettlement(@RequestParam Long cashierId) {
        return payOrderService.dailySettlement(cashierId);
    }

    /** 营收统计（支持多维度筛选） */
    @PostMapping("/revenueStats")
    public Result revenueStats(@RequestBody RevenueStatsDTO dto) {
        return payOrderService.revenueStats(dto);
    }

    /** 标记收据已打印 */
    @PostMapping("/receiptPrinted")
    public Result markReceiptPrinted(@RequestParam String orderNo) {
        return payOrderService.markReceiptPrinted(orderNo);
    }

    // ==================== 退款管理 ====================

    /** 申请退款（收费员/管理员操作） */
    @PostMapping("/refund/apply")
    public Result applyRefund(@RequestBody RefundDTO dto) {
        return payOrderService.applyRefund(dto);
    }

    /** 审核退款（通过/拒绝） */
    @PostMapping("/refund/audit")
    public Result auditRefund(@RequestParam Long refundId,
                              @RequestParam Integer auditResult,
                              @RequestParam(required = false) String auditRemark,
                              @RequestParam Long auditorId) {
        return payOrderService.auditRefund(refundId, auditResult, auditRemark, auditorId);
    }

    /** 查询退款记录 */
    @GetMapping("/refund/list/{patientId}")
    public Result refundList(@PathVariable Long patientId) {
        return payOrderService.refundList(patientId);
    }

    // ==================== 患者端 ====================

    /** 我的缴费单（分页） */
    @GetMapping("/myList")
    public Result myList(@RequestHeader("patientId") Long patientId,
                         @RequestParam(defaultValue = "1") Integer page,
                         @RequestParam(defaultValue = "20") Integer size) {
        return payOrderService.myList(patientId, page, size);
    }

    /** 缴费单详情（含费用明细） */
    @GetMapping("/detail/{orderNo}")
    public Result getOrderDetail(@PathVariable String orderNo) {
        return payOrderService.getOrderDetail(orderNo);
    }
}

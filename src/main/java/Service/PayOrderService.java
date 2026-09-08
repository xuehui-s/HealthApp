package Service;

import Dto.*;
import PoJo.BillItem;
import PoJo.PayOrder;

import java.util.List;

public interface PayOrderService {

    /** 医生开立缴费单（含费用明细） */
    Result createPayOrder(PayOrderDTO dto);

    /** 医生作废缴费单 */
    Result invalidOrder(String orderNo);

    /** 收费员查询患者待缴费单 */
    Result getWaitPayByPatient(Long patientId);

    /** 收费员确认缴费 */
    Result pay(PayRequestDTO request);

    /** 患者查看我的缴费单 */
    Result myList(Long patientId, Integer page, Integer size);

    /** 查询缴费单详情（含费用明细） */
    Result getOrderDetail(String orderNo);

    /** 申请退款 */
    Result applyRefund(RefundDTO dto);

    /** 审核退款（通过/拒绝） */
    Result auditRefund(Long refundId, Integer auditResult, String auditRemark, Long auditorId);

    /** 日终结算 */
    Result dailySettlement(Long cashierId);

    /** 营收统计 */
    Result revenueStats(RevenueStatsDTO dto);

    /** 查询退款记录 */
    Result refundList(Long patientId);

    /** 标记收据已打印 */
    Result markReceiptPrinted(String orderNo);
}

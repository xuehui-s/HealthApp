package PoJo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 退款单
 * 支持全额/部分退款，记录退款原因和审批信息
 */
@Data
@TableName("refund_order")
public class RefundOrder {
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 退款流水号（唯一） */
    private String refundNo;

    /** 关联原缴费单号 */
    private String orderNo;

    /** 关联预约ID */
    private Long appointmentId;

    /** 患者ID */
    private Long patientId;

    /** 原缴费金额 */
    private BigDecimal originalAmount;

    /** 退款金额 */
    private BigDecimal refundAmount;

    /** 退款方式: CASH/WECHAT/ALIPAY/BANK_CARD/MEDICARE */
    private String refundMethod;

    /** 退款原因 */
    private String refundReason;

    /** 退款状态: 0-待审核, 1-已退款, 2-已拒绝 */
    private Integer status;

    /** 操作人ID（收费员或医生） */
    private Long operatorId;

    /** 审核人ID */
    private Long auditorId;

    /** 审核备注 */
    private String auditRemark;

    /** 退款时间 */
    private LocalDateTime refundTime;

    /** 审核时间 */
    private LocalDateTime auditTime;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

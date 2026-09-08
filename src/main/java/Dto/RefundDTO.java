package Dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 退款请求DTO
 */
@Data
public class RefundDTO {
    /** 原缴费单号 */
    private String orderNo;

    /** 退款金额（支持部分退款） */
    private BigDecimal refundAmount;

    /** 退款方式 */
    private String refundMethod;

    /** 退款原因 */
    private String refundReason;

    /** 操作人ID */
    private Long operatorId;
}

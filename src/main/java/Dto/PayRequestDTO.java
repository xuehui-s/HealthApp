package Dto;

import lombok.Data;

/**
 * 缴费请求DTO（收费员确认缴费时使用）
 */
@Data
public class PayRequestDTO {
    /** 缴费单号 */
    private String orderNo;

    /** 支付方式: CASH/WECHAT/ALIPAY/BANK_CARD/MEDICARE */
    private String payMethod;

    /** 收费员ID */
    private Long payerId;

    /** 实收金额 */
    private String receivedAmount;

    /** 支付备注 */
    private String remark;
}

package Dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 日终结算DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SettlementDTO {
    /** 结算日期 */
    private LocalDate settleDate;

    /** 总交易笔数 */
    private Long totalOrders;

    /** 总营收金额 */
    private BigDecimal totalRevenue;

    /** 现金收款 */
    private BigDecimal cashAmount;

    /** 微信收款 */
    private BigDecimal wechatAmount;

    /** 支付宝收款 */
    private BigDecimal alipayAmount;

    /** 银行卡收款 */
    private BigDecimal bankCardAmount;

    /** 医保结算 */
    private BigDecimal medicareAmount;

    /** 退款笔数 */
    private Long refundCount;

    /** 退款总金额 */
    private BigDecimal refundTotal;

    /** 净营收（营收-退款） */
    private BigDecimal netRevenue;

    /** 操作收费员ID */
    private Long cashierId;
}

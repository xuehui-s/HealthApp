package Dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 营收统计查询DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RevenueStatsDTO {
    /** 开始日期 */
    private String startDate;
    /** 结束日期 */
    private String endDate;
    /** 科室ID（可选） */
    private Long deptId;
    /** 医生ID（可选） */
    private Long doctorId;
    /** 支付方式（可选） */
    private String payMethod;
}

/**
 * 营收统计结果
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
class RevenueStatsVO {
    /** 总笔数 */
    private Long totalOrders;
    /** 总营收 */
    private BigDecimal totalRevenue;
    /** 退款笔数 */
    private Long refundCount;
    /** 退款金额 */
    private BigDecimal refundAmount;
    /** 净营收 */
    private BigDecimal netRevenue;
    /** 平均客单价 */
    private BigDecimal avgOrderAmount;
}

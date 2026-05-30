package Dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 缴费单创建请求DTO
 */
@Data
public class PayOrderDTO {
    /**
     * 预约ID
     */
    private Long appointmentId;

    /**
     * 总金额
     */
    private BigDecimal totalAmount;
}

package Dto;

import PoJo.BillItem;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 增强版缴费单创建请求
 * 支持费用明细分项、支付方式等
 */
@Data
public class PayOrderDTO {
    /** 预约ID */
    private Long appointmentId;

    /** 总金额（后端会按明细重新计算校验） */
    private BigDecimal totalAmount;

    /** 费用明细列表 */
    private List<BillItemDTO> items;

    /** 备注（医生填写） */
    private String remark;

    /**
     * 费用明细项DTO
     */
    @Data
    public static class BillItemDTO {
        /** 费用类别 */
        private String category;
        /** 项目名称 */
        private String itemName;
        /** 规格 */
        private String specification;
        /** 单价 */
        private BigDecimal unitPrice;
        /** 数量 */
        private Integer quantity;
        /** 备注 */
        private String remark;
    }
}

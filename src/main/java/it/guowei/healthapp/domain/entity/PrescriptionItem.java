package it.guowei.healthapp.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 处方明细表
 */
@Data
@TableName("prescription_item")
public class PrescriptionItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String prescriptionNo;
    /** 药品ID */
    private Long drugId;
    /** 药品名称 */
    private String drugName;
    /** 规格 */
    private String specification;
    /** 单价 */
    private BigDecimal unitPrice;
    /** 数量 */
    private Integer quantity;
    /** 单位 */
    private String unit;
    /** 用法用量 */
    private String dosage;
    /** 频次 */
    private String frequency;
    /** 天数 */
    private Integer days;
    /** 小计 */
    private BigDecimal subtotal;
    /** 备注 */
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}

package PoJo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 缴费费用明细项
 * 支持将一笔缴费单拆分为多个费用项目（药品费、检查费、诊查费等）
 */
@Data
@TableName("bill_item")
public class BillItem {
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联缴费单号 */
    private String orderNo;

    /** 费用类别: DRUG-药品费, EXAM-检查费, CONSULT-诊查费, MATERIAL-材料费, TREAT-治疗费, OTHER-其他 */
    private String category;

    /** 费用项目名称（如：阿莫西林、血常规） */
    private String itemName;

    /** 规格/说明 */
    private String specification;

    /** 单价 */
    private BigDecimal unitPrice;

    /** 数量 */
    private Integer quantity;

    /** 小计金额 */
    private BigDecimal subtotal;

    /** 备注 */
    private String remark;

    private LocalDateTime createTime;
}

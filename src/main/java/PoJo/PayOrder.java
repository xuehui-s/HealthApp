package PoJo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 增强版缴费单实体
 * 支持企业级支付流程：费用明细、多种支付方式、交易流水号、退款关联
 */
@Data
@TableName("pay_order")
public class PayOrder {
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 缴费单号（雪花ID，唯一） */
    private String orderNo;

    /** 交易流水号（支付成功后生成，格式: TXN + 时间戳 + 随机数，用于审计和对账） */
    private String transactionNo;

    /** 关联预约ID */
    private Long appointmentId;

    /** 患者ID */
    private Long patientId;

    /** 开单医生ID */
    private Long doctorId;

    /** 科室ID */
    private Long deptId;

    /** 总金额 */
    private BigDecimal totalAmount;

    /** 支付方式: CASH/WECHAT/ALIPAY/BANK_CARD/MEDICARE */
    private String payMethod;

    /** 订单状态: 0-待缴费, 1-已缴费, 2-医生作废, 3-超时作废, 4-已退款, 5-部分退款 */
    private Integer status;

    /** 支付时间 */
    private LocalDateTime payTime;

    /** 收费员ID（确认缴费的操作人） */
    private Long payerId;

    /** 过期时间（默认当日23:59:59） */
    private LocalDateTime expireTime;

    /** 医生备注 */
    private String doctorRemark;

    /** 收费备注 */
    private String payRemark;

    /** 是否已打印收据 */
    private Integer receiptPrinted;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

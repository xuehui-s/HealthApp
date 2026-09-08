package it.guowei.healthapp.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 处方表
 */
@Data
@TableName("prescription")
public class Prescription {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String prescriptionNo;
    private Long patientId;
    private Long doctorId;
    private Long appointmentId;
    private Long deptId;

    /** 处方类型：1-西药 2-中成药 3-中药饮片 */
    private Integer type;
    /** 诊断 */
    private String diagnosis;
    /** 总金额 */
    private BigDecimal totalAmount;
    /** 状态：0-待缴费 1-已缴费 2-已发药 3-已退药 */
    private Integer status;
    /** 审核医生ID */
    private Long auditorId;
    /** 审核时间 */
    private LocalDateTime auditTime;
    /** 发药时间 */
    private LocalDateTime dispenseTime;
    /** 备注 */
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}

package it.guowei.healthapp.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 电子病历表
 */
@Data
@TableName("medical_record")
public class MedicalRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long patientId;
    private Long doctorId;
    private Long appointmentId;
    private Long deptId;

    /** 主诉 */
    private String chiefComplaint;
    /** 现病史 */
    private String presentIllness;
    /** 既往史 */
    private String pastHistory;
    /** 过敏史 */
    private String allergyHistory;
    /** 体格检查 */
    private String physicalExam;
    /** 辅助检查 */
    private String auxiliaryExam;
    /** 初步诊断 */
    private String preliminaryDiagnosis;
    /** 治疗意见 */
    private String treatmentPlan;
    /** 医嘱 */
    private String doctorOrder;
    /** 病历状态：0-草稿 1-已提交 2-已审核 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}

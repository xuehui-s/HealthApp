package it.guowei.healthapp.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 操作日志表
 */
@Data
@TableName("operation_log")
public class OperationLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private Integer userType;
    private String username;
    /** 操作模块 */
    private String module;
    /** 操作描述 */
    private String description;
    /** 操作类型 */
    private String operationType;
    /** 请求方法 */
    private String method;
    /** 请求参数 */
    private String params;
    /** 返回结果 */
    private String result;
    /** 操作IP */
    private String ip;
    /** 请求URI */
    private String uri;
    /** 耗时（ms） */
    private Long costTime;
    /** 状态：0-成功 1-失败 */
    private Integer status;
    /** 错误信息 */
    private String errorMsg;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}

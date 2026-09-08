package it.guowei.healthapp.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * AI对话记录表
 */
@Data
@TableName("ai_conversation")
public class AiConversation {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String sessionId;
    private Long userId;
    private Integer userType;
    /** 角色：user/assistant/system */
    private String role;
    /** 内容 */
    private String content;
    /**  tokens */
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;
    /** 耗时（ms） */
    private Long latency;
    /** 模型名称 */
    private String model;
    /** 调用的工具 */
    private String toolsUsed;
    /** 反馈：1-赞 0-踩 null-未反馈 */
    private Integer feedback;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}

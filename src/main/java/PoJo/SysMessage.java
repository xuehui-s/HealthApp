package PoJo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_message")
public class SysMessage {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Integer userType; // 1患者 2医生
    private String title;
    private String content;
    private Integer msgType;
    private Long relationId;
    private Integer isRead;
    private LocalDateTime createTime;
}
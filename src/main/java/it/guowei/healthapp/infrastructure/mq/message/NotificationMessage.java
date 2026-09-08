package it.guowei.healthapp.infrastructure.mq.message;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 通知消息体（可序列化，用于RabbitMQ传输）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotificationMessage implements Serializable {

    private Long userId;
    private Integer userType;
    private String title;
    private String content;
    private Integer msgType;
    private Long relationId;
    private LocalDateTime createTime = LocalDateTime.now();

    public NotificationMessage(Long userId, Integer userType, String title,
                               String content, Integer msgType, Long relationId) {
        this.userId = userId;
        this.userType = userType;
        this.title = title;
        this.content = content;
        this.msgType = msgType;
        this.relationId = relationId;
        this.createTime = LocalDateTime.now();
    }
}

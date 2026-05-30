package Event;

import org.springframework.context.ApplicationEvent;
import java.io.Serializable;

public class MessageEvent extends ApplicationEvent implements Serializable {

    public Long userId;
    public Integer userType;
    public String title;
    public String content;
    public Integer msgType;
    public Long relationId;

    // 必须的无参构造
    public MessageEvent() {
        super(null);
    }

    public MessageEvent(Object source,
                        Long userId, Integer userType,
                        String title, String content,
                        Integer msgType, Long relationId) {
        super(source);
        this.userId = userId;
        this.userType = userType;
        this.title = title;
        this.content = content;
        this.msgType = msgType;
        this.relationId = relationId;
    }
}
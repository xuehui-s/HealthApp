package Service;

import org.springframework.stereotype.Service;


public interface MessageService {

    void sendMessage(Long userId, Integer userType,
                     String title, String content,
                     Integer msgType, Long relationId);

    void read(Long msgId);
}
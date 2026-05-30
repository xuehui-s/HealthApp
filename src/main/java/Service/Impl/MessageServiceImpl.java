package Service.Impl;

import Mapper.MessageMapper;
import PoJo.SysMessage;
import Service.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MessageServiceImpl implements MessageService {

    private final MessageMapper messageMapper;

    // ====================== 统一发送消息 ======================
    @Override
    @Transactional
    public void sendMessage(Long userId, Integer userType,
                            String title, String content,
                            Integer msgType, Long relationId) {
        SysMessage msg = new SysMessage();
        msg.setUserId(userId);
        msg.setUserType(userType);
        msg.setTitle(title);
        msg.setContent(content);
        msg.setMsgType(msgType);
        msg.setRelationId(relationId);
        msg.setIsRead(0);
        messageMapper.insert(msg);
    }

    // ====================== 标记已读 ======================
    @Override
    public void read(Long msgId) {
        SysMessage msg = new SysMessage();
        msg.setId(msgId);
        msg.setIsRead(1);
        messageMapper.updateById(msg);
    }
}
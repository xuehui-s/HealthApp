package Controller;

import Dto.Result;
import Mapper.MessageMapper;
import PoJo.SysMessage;
import Service.MessageService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/message")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;
    private final MessageMapper messageMapper;

    // ====================== 查询我的消息 ======================
    @GetMapping("/my")
    public Result myMessage(@RequestParam Long userId, @RequestParam Integer userType) {
        List<SysMessage> list = messageMapper.selectList(Wrappers.lambdaQuery(SysMessage.class)
                .eq(SysMessage::getUserId, userId)
                .eq(SysMessage::getUserType, userType)
                .orderByDesc(SysMessage::getCreateTime));
        return Result.ok(list);
    }

    // ====================== 查询未读消息数 ======================
    @GetMapping("/unread")
    public Result unread(@RequestParam Long userId, @RequestParam Integer userType) {
        long count = messageMapper.selectCount(Wrappers.lambdaQuery(SysMessage.class)
                .eq(SysMessage::getUserId, userId)
                .eq(SysMessage::getUserType, userType)
                .eq(SysMessage::getIsRead, 0));
        return Result.ok(count);
    }

    // ====================== 标记已读 ======================
    @PostMapping("/read/{id}")
    public Result read(@PathVariable Long id) {
        messageService.read(id);
        return Result.ok("标记成功");
    }
}
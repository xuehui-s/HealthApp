package Controller;

import Dto.Result;
import Service.Impl.AiService;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/ai")
public class AiController {

    private final AiService aiService;

    // 构造器注入，Spring 会自动注入 AiService
    public AiController(AiService aiService) {
        this.aiService = aiService;
    }

    @PostMapping("/chat")
    public Result chat(@RequestBody Map<String, String> request) {
        // 从请求体中获取参数
        String userId = request.get("user_id");
        String question = request.get("question");

        // 简单判空
        if (userId == null || userId.isBlank() || question == null || question.isBlank()) {
            return Result.fail("用户ID和问题不能为空");
        }

        // 调用 Service 处理
        String answer = aiService.chat(userId, question);

        // 返回结果
        return Result.ok(answer);
    }
}
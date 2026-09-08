package Controller;

import Dto.Result;
import it.guowei.healthapp.service.ai.MedicalAgentService;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

/**
 * 旧版 AI 接口（/ai/*）
 * 历史兼容：内部已委托给企业版 MedicalAgentService（→ Python Agent），
 * 原先直写 Redis ai_tasks 队列的方案因无消费者已废弃。
 * 新代码请使用 /api/v1/agent/*。
 */
@RestController
@RequestMapping("/ai")
public class AiController {

    private final MedicalAgentService agentService;

    public AiController(MedicalAgentService agentService) {
        this.agentService = agentService;
    }

    /** 接口1：提交任务（立即返回taskId） */
    @PostMapping("/chat")
    public Result chat(@RequestBody Map<String, String> request) {
        String userId = request.get("user_id");
        String question = request.get("question");

        if (userId == null || userId.isBlank() || question == null || question.isBlank()) {
            return Result.fail("用户ID和问题不能为空");
        }

        try {
            Long uid;
            try {
                uid = Long.parseLong(userId.trim());
            } catch (NumberFormatException e) {
                return Result.fail("用户ID格式错误，必须为数字");
            }
            String sessionId = request.get("session_id");
            String taskId = agentService.submitTask(uid, 1, question, sessionId);
            return Result.ok(Map.of("task_id", taskId, "status", "processing"));
        } catch (Exception e) {
            return Result.fail("提交失败：" + e.getMessage());
        }
    }

    /** 接口2：查询结果（前端轮询调用） */
    @GetMapping("/result/{taskId}")
    public Result getResult(@PathVariable String taskId) {
        Map<String, Object> state = agentService.getResult(taskId);
        if ("processing".equals(state.get("status"))) {
            return Result.ok(Map.of("status", "processing"));
        }
        if ("done".equals(state.get("status"))) {
            return Result.ok(Map.of("status", "done", "answer", state.get("answer")));
        }
        return Result.fail("任务不存在或已过期");
    }
}

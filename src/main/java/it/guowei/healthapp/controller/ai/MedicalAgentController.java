package it.guowei.healthapp.controller.ai;

import it.guowei.healthapp.common.annotation.RateLimit;
import it.guowei.healthapp.common.context.UserContext;
import it.guowei.healthapp.common.result.Result;
import it.guowei.healthapp.service.ai.MedicalAgentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

/**
 * 医疗智能Agent接口（企业级）
 * 面向医生和患者，提供AI辅助诊断、知识问答、病历分析等
 */
@Tag(name = "医疗智能Agent", description = "AI辅助诊断、知识问答、流式对话")
@Slf4j
@RestController
@RequestMapping("/api/v1/agent")
@RequiredArgsConstructor
public class MedicalAgentController {

    private final MedicalAgentService agentService;

    @Operation(summary = "提交AI对话任务（异步，返回taskId供轮询）")
    @PostMapping("/chat")
    @RateLimit(key = "agent:chat:", time = 60, count = 30, type = RateLimit.LimitType.USER)
    public Result<Map<String, String>> chat(@RequestBody Map<String, String> request) {
        Long userId = UserContext.getUserId();
        Integer userType = UserContext.getUserType() != null ? UserContext.getUserType() : 1;
        String question = request.get("question");
        String sessionId = request.get("session_id");

        String taskId = agentService.submitTask(userId, userType, question, sessionId);
        return Result.ok(Map.of("task_id", taskId, "status", "processing"));
    }

    @Operation(summary = "查询AI任务结果")
    @GetMapping("/result/{taskId}")
    public Result<Map<String, Object>> getResult(@PathVariable String taskId) {
        return Result.ok(agentService.getResult(taskId));
    }

    @Operation(summary = "SSE流式对话（实时输出）")
    @GetMapping(value = "/stream", produces = "text/event-stream")
    public SseEmitter streamChat(@RequestParam String question,
                                 @RequestParam(required = false) String sessionId) {
        Long userId = UserContext.getUserId();
        Integer userType = UserContext.getUserType() != null ? UserContext.getUserType() : 1;
        return agentService.streamChat(userId, userType, question, sessionId);
    }

    @Operation(summary = "获取对话历史")
    @GetMapping("/history")
    public Result<List<Map<String, Object>>> getHistory(@RequestParam(required = false) String sessionId) {
        Long userId = UserContext.getUserId();
        return Result.ok(agentService.getHistory(sessionId, userId));
    }

    @Operation(summary = "清空对话历史")
    @DeleteMapping("/history")
    public Result<Void> clearHistory(@RequestParam(required = false) String sessionId) {
        Long userId = UserContext.getUserId();
        agentService.clearHistory(sessionId, userId);
        return Result.ok();
    }
}

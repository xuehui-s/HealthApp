package it.guowei.healthapp.service.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.guowei.healthapp.common.exception.BusinessException;
import it.guowei.healthapp.common.result.ResultCode;
import it.guowei.healthapp.domain.entity.AiConversation;
import it.guowei.healthapp.infrastructure.mapper.AiConversationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.annotation.Resource;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 医疗智能Agent网关服务
 * 职责：鉴权与限流之后的 AI 请求转发 —— 本服务不实现任何 AI 逻辑，
 * 全部推理由 Python Agent（FastAPI + ReAct + RAG）完成，这里只做：
 * 1. 异步任务模式：提交 → 线程池调用 Python /chat → 结果写 Redis → 前端轮询
 * 2. SSE 流式模式：转发 Python /chat/stream 的事件流给前端（逐事件透传）
 * 3. 对话历史（Redis，JSON 结构）与 AI 对话审计落库（ai_conversation）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MedicalAgentService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final AiConversationMapper aiConversationMapper;

    @Resource(name = "agentExecutor")
    private java.util.concurrent.Executor agentExecutor;

    @Value("${app.agent.url:http://localhost:8000}")
    private String agentUrl;

    @Value("${app.ai.result-expire:3600}")
    private long resultExpireSeconds;

    private static final String TASK_KEY_PREFIX = "ai:task:";
    private static final String RESULT_KEY_PREFIX = "ai:result:";
    private static final String HISTORY_KEY_PREFIX = "ai:history:";

    /** OkHttp 线程安全，全局复用；流式读超时给足（LLM 多轮工具调用耗时较长） */
    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build();

    private static final MediaType JSON_TYPE = MediaType.parse("application/json; charset=utf-8");

    // ==================================================================
    // 模式一：异步任务（submit → poll）
    // ==================================================================
    public String submitTask(Long userId, Integer userType, String question, String sessionId) {
        if (question == null || question.isBlank()) {
            throw new BusinessException(ResultCode.AI_QUESTION_EMPTY);
        }

        String taskId = UUID.randomUUID().toString().replace("-", "");
        String sid = sessionId != null && !sessionId.isBlank() ? sessionId : "u" + userId;

        redisTemplate.opsForValue().set(TASK_KEY_PREFIX + taskId, "processing", 10, TimeUnit.MINUTES);
        pushHistory(sid, "user", question);

        agentExecutor.execute(() -> doSyncTask(taskId, userId, userType, question, sid));
        log.info("AI任务提交: taskId={}, userId={}, sessionId={}", taskId, userId, sid);
        return taskId;
    }

    private void doSyncTask(String taskId, Long userId, Integer userType, String question, String sessionId) {
        long start = System.currentTimeMillis();
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("user_id", userId != null ? userId : 0);
            body.put("user_type", userType != null ? userType : 1);
            body.put("question", question);
            body.put("session_id", sessionId);

            Request request = new Request.Builder()
                    .url(agentUrl + "/api/v1/agent/chat")
                    .post(RequestBody.create(objectMapper.writeValueAsString(body), JSON_TYPE))
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    throw new IllegalStateException("Agent服务响应异常: HTTP " + response.code());
                }
                JsonNode root = objectMapper.readTree(response.body().string());
                JsonNode data = root.path("data");
                String answer = data.path("answer").asText("");

                redisTemplate.opsForValue().set(RESULT_KEY_PREFIX + taskId, answer,
                        resultExpireSeconds, TimeUnit.SECONDS);
                redisTemplate.opsForValue().set(TASK_KEY_PREFIX + taskId, "done",
                        10, TimeUnit.MINUTES);
                pushHistory(sessionId, "assistant", answer);
                saveConversations(sessionId, userId, userType, question, data,
                        System.currentTimeMillis() - start);
            }
        } catch (Exception e) {
            log.error("AI任务执行失败: taskId={}", taskId, e);
            redisTemplate.opsForValue().set(RESULT_KEY_PREFIX + taskId,
                    "AI服务暂时不可用，请稍后再试。", resultExpireSeconds, TimeUnit.SECONDS);
            redisTemplate.opsForValue().set(TASK_KEY_PREFIX + taskId, "done",
                    10, TimeUnit.MINUTES);
        }
    }

    /** 轮询任务结果：优先查结果键，任务键仅用于判断"处理中/不存在" */
    public Map<String, Object> getResult(String taskId) {
        String answer = redisTemplate.opsForValue().get(RESULT_KEY_PREFIX + taskId);
        if (answer != null) {
            return Map.of("status", "done", "answer", answer);
        }
        String status = redisTemplate.opsForValue().get(TASK_KEY_PREFIX + taskId);
        if ("processing".equals(status)) {
            return Map.of("status", "processing");
        }
        return Map.of("status", "not_found");
    }

    // ==================================================================
    // 模式二：SSE 流式（Python 事件流逐条透传给前端）
    // 事件协议见 python-agent/app/agent/medical_agent.py 头注释：
    //   start/status/content/tool_call/tool_result/done/error + [DONE]
    // ==================================================================
    public SseEmitter streamChat(Long userId, Integer userType, String question, String sessionId) {
        if (question == null || question.isBlank()) {
            throw new BusinessException(ResultCode.AI_QUESTION_EMPTY);
        }
        String sid = sessionId != null && !sessionId.isBlank() ? sessionId : "u" + userId;
        SseEmitter emitter = new SseEmitter(180_000L);

        agentExecutor.execute(() -> {
            long start = System.currentTimeMillis();
            StringBuilder fullAnswer = new StringBuilder();
            try {
                Map<String, Object> body = new HashMap<>();
                body.put("user_id", userId != null ? userId : 0);
                body.put("user_type", userType != null ? userType : 1);
                body.put("question", question);
                body.put("session_id", sid);

                Request request = new Request.Builder()
                        .url(agentUrl + "/api/v1/agent/chat/stream")
                        .post(RequestBody.create(objectMapper.writeValueAsString(body), JSON_TYPE))
                        .header("Accept", "text/event-stream")
                        .build();

                try (Response response = httpClient.newCall(request).execute()) {
                    if (!response.isSuccessful() || response.body() == null) {
                        throw new IllegalStateException("Agent服务响应异常: HTTP " + response.code());
                    }
                    pushHistory(sid, "user", question);

                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(response.body().byteStream(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            if (!line.startsWith("data:")) {
                                continue;
                            }
                            String payload = line.substring(5).trim();
                            if ("[DONE]".equals(payload)) {
                                break;
                            }
                            // 透传事件给前端；同时累积正文用于落库
                            emitter.send(SseEmitter.event().data(payload));
                            try {
                                JsonNode event = objectMapper.readTree(payload);
                                if ("content".equals(event.path("type").asText())) {
                                    fullAnswer.append(event.path("delta").asText());
                                }
                            } catch (Exception ignore) {
                                // 非JSON事件直接透传
                            }
                        }
                    }
                }
                emitter.complete();

                String answer = fullAnswer.toString();
                if (!answer.isBlank()) {
                    redisTemplate.opsForValue().set(RESULT_KEY_PREFIX + "stream-" + sid, answer,
                            resultExpireSeconds, TimeUnit.SECONDS);
                    saveConversationPair(sid, userId, userType, question, answer, null,
                            System.currentTimeMillis() - start);
                }
            } catch (Exception e) {
                log.error("SSE流式对话失败: userId={}", userId, e);
                try {
                    emitter.send(SseEmitter.event().data(
                            "{\"type\":\"error\",\"message\":\"AI服务暂时不可用，请稍后再试\"}"));
                    emitter.complete();
                } catch (Exception ignore) {
                    emitter.completeWithError(e);
                }
            }
        });
        return emitter;
    }

    // ==================================================================
    // 对话历史（Redis List，元素为 JSON：{role, content, ts}）
    // ==================================================================
    public List<Map<String, Object>> getHistory(String sessionId, Long userId) {
        String key = historyKey(sessionId, userId);
        List<String> raw = redisTemplate.opsForList().range(key, -40, -1);
        List<Map<String, Object>> result = new ArrayList<>();
        if (raw == null) {
            return result;
        }
        for (String item : raw) {
            try {
                result.add(objectMapper.readValue(item, new TypeReference<Map<String, Object>>() {}));
            } catch (Exception ignore) {
                // 兼容旧格式 "USER:xxx"
                if (item.startsWith("USER:")) {
                    result.add(Map.of("role", "user", "content", item.substring(5)));
                } else if (item.startsWith("ASSISTANT:")) {
                    result.add(Map.of("role", "assistant", "content", item.substring(10)));
                }
            }
        }
        return result;
    }

    public void clearHistory(String sessionId, Long userId) {
        redisTemplate.delete(historyKey(sessionId, userId));
    }

    private String historyKey(String sessionId, Long userId) {
        return HISTORY_KEY_PREFIX + (sessionId != null && !sessionId.isBlank() ? sessionId : userId);
    }

    private void pushHistory(String sessionId, String role, String content) {
        try {
            String json = objectMapper.writeValueAsString(
                    Map.of("role", role, "content", content, "ts", System.currentTimeMillis()));
            String key = HISTORY_KEY_PREFIX + sessionId;
            redisTemplate.opsForList().rightPush(key, json);
            redisTemplate.opsForList().trim(key, -40, -1); // 滑动窗口
            redisTemplate.expire(key, 24, TimeUnit.HOURS);
        } catch (Exception e) {
            log.warn("对话历史写入失败: {}", e.getMessage());
        }
    }

    // ==================================================================
    // AI 对话审计（ai_conversation 表，管理端可查）
    // ==================================================================
    private void saveConversations(String sessionId, Long userId, Integer userType,
                                   String question, JsonNode data, long latency) {
        String answer = data.path("answer").asText("");
        saveConversationPair(sessionId, userId, userType, question, answer, data, latency);
    }

    private void saveConversationPair(String sessionId, Long userId, Integer userType,
                                      String question, String answer, JsonNode data, long latency) {
        try {
            JsonNode usage = data != null ? data.path("usage") : null;
            List<String> tools = new ArrayList<>();
            if (data != null) {
                data.path("tools_used").forEach(n -> tools.add(n.asText()));
            }

            AiConversation userMsg = new AiConversation();
            userMsg.setSessionId(sessionId);
            userMsg.setUserId(userId != null ? userId : 0L);
            userMsg.setUserType(userType != null ? userType : 1);
            userMsg.setRole("user");
            userMsg.setContent(question);
            userMsg.setCreateTime(LocalDateTime.now());
            aiConversationMapper.insert(userMsg);

            AiConversation aiMsg = new AiConversation();
            aiMsg.setSessionId(sessionId);
            aiMsg.setUserId(userId != null ? userId : 0L);
            aiMsg.setUserType(userType != null ? userType : 1);
            aiMsg.setRole("assistant");
            aiMsg.setContent(answer);
            if (usage != null && !usage.isMissingNode()) {
                aiMsg.setPromptTokens(usage.path("prompt_tokens").asInt(0));
                aiMsg.setCompletionTokens(usage.path("completion_tokens").asInt(0));
                aiMsg.setTotalTokens(usage.path("total_tokens").asInt(0));
            }
            aiMsg.setLatency(latency);
            aiMsg.setToolsUsed(String.join(",", tools));
            aiMsg.setCreateTime(LocalDateTime.now());
            aiConversationMapper.insert(aiMsg);
        } catch (Exception e) {
            log.warn("AI对话审计落库失败: {}", e.getMessage());
        }
    }
}

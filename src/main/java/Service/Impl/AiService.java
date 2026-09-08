package Service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import PoJo.Appointment;
import Service.AppointmentService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class AiService {

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private AppointmentService appointmentService;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${ai.result.expire:3600}")
    private long resultExpireSeconds;  // 结果过期时间，默认1小时

    /**
     * 提交AI任务到消息队列（异步）
     * @return taskId 任务ID，用于后续查询结果
     */
    public String submitTask(String userId, String question) {
        // 1. 转换用户ID
        Integer patientId;
        try {
            patientId = Integer.parseInt(userId.trim());
        } catch (NumberFormatException e) {
            throw new RuntimeException("用户ID格式错误，必须为数字");
        }

        // 2. 查询预约记录
        List<Appointment> appointments = appointmentService.list(
                new LambdaQueryWrapper<Appointment>()
                        .eq(Appointment::getPatientId, patientId)
                        .eq(Appointment::getStatus, 0)
                        .orderByAsc(Appointment::getAppointDate)
        );

        // 3. 拼接用户数据
        StringBuilder userData = new StringBuilder("用户预约记录：\n");
        if (appointments.isEmpty()) {
            userData.append("暂无预约记录。");
        } else {
            for (Appointment a : appointments) {
                userData.append("- 日期：").append(a.getAppointDate())
                        .append("，时段：").append(a.getTimePeriod())
                        .append("\n");
            }
        }

        String finalQuestion = String.format("用户预约记录：%s\n用户问题：%s\n请根据预约记录回答用户的问题。", userData, question);

        // 4. 生成任务ID
        String taskId = UUID.randomUUID().toString();

        // 5. 构造任务消息
        Map<String, String> task = new HashMap<>();
        task.put("task_id", taskId);
        task.put("user_id", userId);
        task.put("question", finalQuestion);

        try {
            String taskJson = objectMapper.writeValueAsString(task);
            // 发送到Redis消息队列（左侧推入）
            redisTemplate.opsForList().leftPush("ai_tasks", taskJson);

            // 初始化结果状态为"processing"
            redisTemplate.opsForValue().set("ai_result:" + taskId, "processing", 10, TimeUnit.MINUTES);

            return taskId;
        } catch (Exception e) {
            throw new RuntimeException("提交AI任务失败：" + e.getMessage());
        }
    }

    /**
     * 查询AI任务结果
     * @return null=处理中，String=最终答案
     */
    public String getResult(String taskId) {
        String resultKey = "ai_result:" + taskId;
        String result = redisTemplate.opsForValue().get(resultKey);

        // 如果结果是"processing"，说明还在处理中
        if ("processing".equals(result)) {
            return null;  // 返回null表示处理中
        }

        return result;  // 返回最终答案
    }
}
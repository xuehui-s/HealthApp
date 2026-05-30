package Service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import PoJo.Appointment;
import Service.AppointmentService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AiService {

    @Autowired
    private AppointmentService appointmentService;

    private final RestTemplate restTemplate;
    private final String aiServiceUrl;

    // 构造器注入
    public AiService(RestTemplate restTemplate,
                     @Value("${ai.service.url:http://localhost:8000/ai/chat}") String aiServiceUrl) {
        this.restTemplate = restTemplate;
        this.aiServiceUrl = aiServiceUrl;
    }

    public String chat(String userId, String question) {
        // 1. 转换用户ID（patient_id 是 int）
        Integer patientId;
        try {
            patientId = Integer.parseInt(userId.trim());
        } catch (NumberFormatException e) {
            return "用户ID格式错误，必须为数字";
        }

        // 2. 用 list() 查询预约记录（现在这个方法存在了）
        List<Appointment> appointments = appointmentService.list(
                new LambdaQueryWrapper<Appointment>()
                        .eq(Appointment::getPatientId, patientId)
                        .eq(Appointment::getStatus, 0) // 只查未签到预约
                        .orderByAsc(Appointment::getAppointDate)
        );

        // 3. 拼接用户预约信息
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

        // 4. 把用户数据和问题一起传给 AI
        String finalQuestion = String.format("""
用户预约记录：%s
用户问题：%s
请根据预约记录回答用户的问题，如果记录为空请如实告知。
""", userData, question);

        // 5. 调用 FastAPI 接口
        Map<String, String> request = new HashMap<>();
        request.put("user_id", userId);
        request.put("question", finalQuestion);

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(
                    aiServiceUrl,
                    request,
                    Map.class
            );

            if (response != null && Integer.parseInt(response.get("code").toString()) == 200) {
                return (String) response.get("answer");
            } else {
                return "AI 服务返回异常";
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "调用异常：" + e.getMessage();
        }
    }
}
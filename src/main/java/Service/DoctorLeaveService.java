package Service;

import Dto.Result;
import PoJo.DoctorLeave;
import java.time.LocalDate;
import java.util.List;

public interface DoctorLeaveService {

    // 常规请假（必须≥7天后）
    Result normalLeave(DoctorLeave leave, Integer doctorId);

    // 紧急请假（1-6天，自动取消预约）
    Result emergencyLeave(DoctorLeave leave, Integer doctorId);

    // 取消请假
    Result cancelLeave(Integer id, Integer doctorId);

    // 查看我的请假记录
    List<DoctorLeave> myLeaveList(Integer doctorId);

    // 判断医生是否请假
    boolean isOnLeave(Integer doctorId, LocalDate date, String period);
}
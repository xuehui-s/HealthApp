package Service;

import Dto.AppointmentDTO;
import PoJo.Appointment;
import Vo.DayStatusVO;
import Vo.PeriodStatusVO;
import Dto.Result;
import com.baomidou.mybatisplus.extension.service.IService;

import java.time.LocalDate;
import java.util.List;

public interface AppointmentService extends IService<Appointment> {

    // 提交预约
    Result submit(AppointmentDTO dto, Integer patientId);

    // 取消预约
    Result cancel(Integer id, Integer patientId);

    // 获取未来7天号源状态
    Result get7DayStatus(Integer deptId);

    // 获取时段可约状态
    Result getPeriodStatus(Integer deptId, LocalDate date, String period);

    // 查询我的预约
    List<Appointment> getMyAppointments(Integer patientId);
    
    // 查询医生的预约（医生端用）
    List<Appointment> getByDoctor(Integer doctorId);
    boolean isDoctorOnLeave(Integer doctorId, LocalDate date, String period);
}
package Controller;

import Dto.AppointmentDTO;
import PoJo.Appointment;
import PoJo.Doctor;
import Service.AppointmentService;
import Service.DoctorLoginService;
import Service.DepartmentService;
import Vo.DoctorVO;
import Dto.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/appointment")
@RequiredArgsConstructor
public class PatientAppointmentController {

    private final AppointmentService appointmentService;
    private final DepartmentService departmentService;
    private final DoctorLoginService doctorService;

    // 1. 获取所有科室
    @GetMapping("/dept/list")
    public Result deptList() {
        return Result.ok(departmentService.list());
    }

    // 2. 获取7天号源状态
    @GetMapping("/day/status")
    public Result dayStatus(@RequestParam Integer deptId) {
        return appointmentService.get7DayStatus(deptId);
    }

    // 3. 获取时段可约状态
    @GetMapping("/period/status")
    public Result periodStatus(
            @RequestParam Integer deptId,
            @RequestParam String date,
            @RequestParam String period) {
        return appointmentService.getPeriodStatus(deptId, LocalDate.parse(date), period);
    }

    // 4. 根据科室获取医生（仅基础信息，后台用）
    @GetMapping("/doctor/list")
    public Result doctorList(@RequestParam Integer deptId) {
        return Result.ok(doctorService.getByDeptId(deptId));
    }

    // 5. 根据科室+日期+时段获取医生（带请假状态，前端预约用）
    @GetMapping("/doctor/list/with-leave")
    public Result doctorListWithLeave(@RequestParam Integer deptId,
                                      @RequestParam String date,
                                      @RequestParam String period) {
        List<Doctor> list = doctorService.getByDeptId(deptId);
        List<DoctorVO> voList = new ArrayList<>();

        LocalDate parseDate = LocalDate.parse(date);
        for (Doctor d : list) {
            DoctorVO vo = new DoctorVO();
            vo.setId(d.getId());
            vo.setName(d.getName());
            // 判断医生在指定日期+时段是否请假
            boolean onLeave = appointmentService.isDoctorOnLeave(d.getId(), parseDate, period);
            vo.setOnLeave(onLeave); // 前端根据这个字段置灰
            voList.add(vo);
        }
        return Result.ok(voList);
    }

    // 6. 提交预约
    @PostMapping("/submit")
    public Result submit(@RequestBody AppointmentDTO dto,
                         @RequestHeader("patientId") Integer patientId) {
        return appointmentService.submit(dto, patientId);
    }

    // 7. 我的预约
    @GetMapping("/my")
    public Result my(@RequestHeader("patientId") Integer patientId) {
        List<Appointment> list = appointmentService.getMyAppointments(patientId);
        return Result.ok(list);
    }

    // 8. 取消预约
    @PostMapping("/cancel")
    public Result cancel(@RequestParam Integer id,
                         @RequestHeader("patientId") Integer patientId) {
        return appointmentService.cancel(id, patientId);
    }
}
package Controller;

import Dto.Result;
import PoJo.DoctorLeave;
import PoJo.Appointment;
import Service.AppointmentService;
import Service.DoctorLeaveService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/doctor/appointment")
@RequiredArgsConstructor
public class DoctorAppointmentController {

    private final AppointmentService appointmentService;
    private final DoctorLeaveService doctorLeaveService;

    // ====================== 查看我的预约 ======================
    @GetMapping("/my")
    public Result myAppoint(@RequestHeader(value = "doctorId", required = false) Integer doctorId) {
        if (doctorId == null) {
            return Result.fail("未登录或Token无效");
        }
        log.info("医生{}查询我的预约", doctorId);
        List<Appointment> list = appointmentService.getByDoctor(doctorId);
        return Result.ok(list);
    }

    // ====================== 常规请假 ======================
    @PostMapping("/leave/normal")
    public Result normalLeave(@RequestBody DoctorLeave leave,
                              @RequestHeader(value = "doctorId", required = false) Integer doctorId) {
        if (doctorId == null) {
            return Result.fail("未登录或Token无效");
        }
        log.info("医生{}申请常规请假: {} {}", doctorId, leave.getLeaveDate(), leave.getTimePeriod());
        return doctorLeaveService.normalLeave(leave, doctorId);
    }

    // ====================== 紧急请假 ======================
    @PostMapping("/leave/emergency")
    public Result emergencyLeave(@RequestBody DoctorLeave leave,
                                 @RequestHeader(value = "doctorId", required = false) Integer doctorId) {
        if (doctorId == null) {
            return Result.fail("未登录或Token无效");
        }
        log.info("医生{}申请紧急请假: {} {}", doctorId, leave.getLeaveDate(), leave.getTimePeriod());
        return doctorLeaveService.emergencyLeave(leave, doctorId);
    }

    // ====================== 取消请假 ======================
    @PostMapping("/leave/cancel")
    public Result cancelLeave(@RequestParam Integer id,
                              @RequestHeader(value = "doctorId", required = false) Integer doctorId) {
        if (doctorId == null) {
            return Result.fail("未登录或Token无效");
        }
        log.info("医生{}取消请假ID: {}", doctorId, id);
        return doctorLeaveService.cancelLeave(id, doctorId);
    }

    // ====================== 我的请假记录 ======================
    @GetMapping("/leave/my")
    public Result myLeave(@RequestHeader(value = "doctorId", required = false) Integer doctorId) {
        if (doctorId == null) {
            return Result.fail("未登录或Token无效");
        }
        log.info("医生{}查询请假记录", doctorId);
        return Result.ok(doctorLeaveService.myLeaveList(doctorId));
    }
}
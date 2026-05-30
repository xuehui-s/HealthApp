package Service.Impl;

import Dto.AppointmentDTO;
import Event.MessageEvent;
import Mapper.AppointmentMapper;
import Mapper.DoctorMapper;
import Mapper.PatientLimitMapper;
import PoJo.Appointment;
import PoJo.Doctor;
import PoJo.PatientLimit;
import Service.AppointmentService;
import Service.DoctorLeaveService;
import Vo.DayStatusVO;
import Vo.PeriodStatusVO;
import Dto.Result;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class AppointmentServiceImpl extends ServiceImpl<AppointmentMapper, Appointment> implements AppointmentService {

    private final AppointmentMapper appointmentMapper;
    private final PatientLimitMapper patientLimitMapper;
    private final DoctorMapper doctorMapper;
    private final RedissonClient redissonClient;
    private final DoctorLeaveService doctorLeaveService;
    @Autowired
    private ApplicationContext applicationContext;

    public static final int MAX_PER_PERIOD = 15;
    public static final int MAX_APPOINT_DAY = 1;
    public static final int MAX_CANCEL_DAY = 3;

    // ====================== 提交预约 ======================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result submit(AppointmentDTO dto, Integer patientId) {
        String lockKey = "appoint:lock:" + dto.getDoctorId() + ":" + dto.getAppointDate() + ":" + dto.getTimePeriod();
        RLock lock = redissonClient.getLock(lockKey);

        try {
            boolean locked = lock.tryLock(3, 10, TimeUnit.SECONDS);
            if (!locked) {
                return Result.fail("系统繁忙，请稍后重试");
            }

            LocalDate now = LocalDate.now();

            if (dto.getAppointDate().isBefore(now)) {
                return Result.fail("不能预约过去的日期");
            }

            if (dto.getAppointDate().isAfter(now.plusDays(7))) {
                return Result.fail("只能预约未来7天内");
            }

            if (dto.getAppointDate().isEqual(now)) {
                int currentHour = LocalTime.now().getHour();
                if ("上午".equals(dto.getTimePeriod()) && currentHour >= 12) {
                    return Result.fail("上午时段已截止");
                }
                if ("下午".equals(dto.getTimePeriod()) && currentHour >= 18) {
                    return Result.fail("下午时段已截止");
                }
            }

            Doctor doctor = doctorMapper.selectById(dto.getDoctorId());
            if (doctor == null) {
                return Result.fail("医生不存在");
            }

            if (doctor.getStatus() != null && doctor.getStatus() != 1) {
                return Result.fail("该医生已停诊，请选择其他医生");
            }

            if (!doctor.getDepartmentId().equals(dto.getDeptId())) {
                return Result.fail("科室与医生不匹配");
            }

            // ========== 【医生请假判断】患者端核心拦截 ==========
            if (isDoctorOnLeave(doctor.getId(), dto.getAppointDate(), dto.getTimePeriod())) {
                return Result.fail("该医生此时段请假，无法预约");
            }
            // ===================================================

            long existCount = appointmentMapper.selectCount(Wrappers.lambdaQuery(Appointment.class)
                    .eq(Appointment::getPatientId, patientId)
                    .eq(Appointment::getDoctorId, dto.getDoctorId())
                    .eq(Appointment::getAppointDate, dto.getAppointDate())
                    .eq(Appointment::getTimePeriod, dto.getTimePeriod())
                    .eq(Appointment::getStatus, 0));

            if (existCount > 0) {
                return Result.fail("您已经预约过该时段，请勿重复预约");
            }

            long todayAppointCount = appointmentMapper.selectCount(Wrappers.lambdaQuery(Appointment.class)
                    .eq(Appointment::getPatientId, patientId)
                    .eq(Appointment::getAppointDate, dto.getAppointDate())
                    .eq(Appointment::getStatus, 0));

            if (todayAppointCount >= MAX_APPOINT_DAY) {
                return Result.fail("一天只能预约一次");
            }

            int count = appointmentMapper.countByDoctorAndDatePeriod(
                    dto.getDoctorId(), dto.getAppointDate(), dto.getTimePeriod());

            if (count >= MAX_PER_PERIOD) {
                return Result.fail("该时段已满员");
            }

            LambdaQueryWrapper<PatientLimit> limitWrapper = Wrappers.lambdaQuery(PatientLimit.class)
                    .eq(PatientLimit::getPatientId, patientId)
                    .eq(PatientLimit::getDate, dto.getAppointDate());

            PatientLimit limit = patientLimitMapper.selectOne(limitWrapper);
            if (limit == null) {
                limit = new PatientLimit();
                limit.setPatientId(patientId);
                limit.setDate(dto.getAppointDate());
                limit.setAppointCount(0);
                limit.setCancelCount(0);
                patientLimitMapper.insert(limit);
            }

            if (limit.getDate().isEqual(LocalDate.now()) && limit.getCancelCount() >= MAX_CANCEL_DAY) {
                return Result.fail("今日取消次数已达上限（3次）");
            }

            int queueNum = count + 1;

            Appointment appointment = new Appointment();
            appointment.setPatientId(patientId);
            appointment.setDeptId(dto.getDeptId());
            appointment.setDoctorId(dto.getDoctorId());
            appointment.setAppointDate(dto.getAppointDate());
            appointment.setTimePeriod(dto.getTimePeriod());
            appointment.setQueueNum(queueNum);
            appointment.setFrontCount(count);
            appointment.setStatus(0);

            appointmentMapper.insert(appointment);
            // ====================== 发送预约成功通知 ======================
            applicationContext.publishEvent(new MessageEvent(
                    this,
                    Long.valueOf(patientId),
                    1, // 1=患者
                    "预约成功",
                    "您已成功预约【" + doctor.getName() + "】医生，时间：" + dto.getAppointDate() + " " + dto.getTimePeriod(),
                    1, // 消息类型 1=预约成功
                    Long.valueOf(appointment.getId())
            ));
            // ====================== 发给医生：新预约提醒 ======================
            applicationContext.publishEvent(new MessageEvent(
                    this,
                    Long.valueOf(dto.getDoctorId()),
                    2,  // 2=医生
                    "新预约通知",
                    "您有新的预约：" + dto.getAppointDate() + " " + dto.getTimePeriod(),
                    6,
                    Long.valueOf(appointment.getId())
            ));



            limit.setAppointCount(limit.getAppointCount() + 1);
            patientLimitMapper.updateById(limit);

            Map<String, Object> map = new HashMap<>();
            map.put("queueNum", queueNum);
            map.put("frontCount", count);
            map.put("appointment", appointment);

            return Result.ok("预约成功", map);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Result.fail("系统繁忙，请稍后重试");
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    // ====================== 取消预约 ======================
    @Override
    @Transactional
    public Result cancel(Integer id, Integer patientId) {
        Appointment appointment = appointmentMapper.selectById(id);
        if (appointment == null) {
            return Result.fail("预约不存在");
        }

        if (!appointment.getPatientId().equals(patientId)) {
            return Result.fail("无权限取消该预约");
        }

        if (appointment.getStatus() != 0) {
            return Result.fail("只能取消待就诊的预约");
        }

        LocalDate now = LocalDate.now();
        if (appointment.getAppointDate().isBefore(now)) {
            return Result.fail("不能取消过去的预约");
        }

        if (appointment.getAppointDate().isEqual(now)) {
            int currentHour = LocalTime.now().getHour();
            if ("上午".equals(appointment.getTimePeriod()) && currentHour >= 12) {
                return Result.fail("上午时段已截止，无法取消");
            }
            if ("下午".equals(appointment.getTimePeriod()) && currentHour >= 18) {
                return Result.fail("下午时段已截止，无法取消");
            }
        }

        String lockKey = "appoint:cancel:" + id;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            boolean locked = lock.tryLock(3, 5, TimeUnit.SECONDS);
            if (!locked) {
                return Result.fail("系统繁忙，请稍后重试");
            }

            appointment = appointmentMapper.selectById(id);
            if (appointment.getStatus() != 0) {
                return Result.fail("该预约已被取消或处理");
            }

            LambdaQueryWrapper<PatientLimit> wrapper = Wrappers.lambdaQuery(PatientLimit.class)
                    .eq(PatientLimit::getPatientId, patientId)
                    .eq(PatientLimit::getDate, appointment.getAppointDate());

            PatientLimit limit = patientLimitMapper.selectOne(wrapper);

            if (limit == null) {
                appointment.setStatus(2);
                appointmentMapper.updateById(appointment);
                // ====================== 发送取消预约通知 ======================
                applicationContext.publishEvent(new MessageEvent(
                        this,
                        Long.valueOf(patientId),
                        1,
                        "取消预约成功",
                        "您已成功取消 " + appointment.getAppointDate() + " " + appointment.getTimePeriod() + " 的预约",
                        2, // 2=取消预约
                        Long.valueOf(appointment.getId())
                ));
                // ====================== 发给医生：患者取消预约 ======================
                applicationContext.publishEvent(new MessageEvent(
                        this,
                        Long.valueOf(appointment.getDoctorId()),
                        2,
                        "预约被取消",
                        "患者取消了：" + appointment.getAppointDate() + " " + appointment.getTimePeriod(),
                        7,
                        Long.valueOf(appointment.getId())
                ));

                return Result.ok("取消成功");
            }

            if (appointment.getAppointDate().isEqual(LocalDate.now()) && limit.getCancelCount() >= MAX_CANCEL_DAY) {
                return Result.fail("今日取消次数已达上限（3次）");
            }

            appointment.setStatus(2);
            appointmentMapper.updateById(appointment);

            if (limit.getAppointCount() > 0) {
                limit.setAppointCount(limit.getAppointCount() - 1);
            }

            if (appointment.getAppointDate().isEqual(LocalDate.now())) {
                limit.setCancelCount(limit.getCancelCount() + 1);
            }

            patientLimitMapper.updateById(limit);

            return Result.ok("取消成功，可重新预约");

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Result.fail("系统繁忙，请稍后重试");
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    // ====================== 7天号源状态（修复完成） ======================
    @Override
    public Result get7DayStatus(Integer deptId) {
        List<DayStatusVO> list = new ArrayList<>();
        LocalDate now = LocalDate.now();

        for (int i = 0; i < 7; i++) {
            LocalDate date = now.plusDays(i);

            List<Doctor> allDoctors = doctorMapper.selectList(Wrappers.lambdaQuery(Doctor.class)
                    .eq(Doctor::getDepartmentId, deptId)
                    .eq(Doctor::getStatus, 1));

            int leaveDoctorCount = 0;
            for (Doctor doc : allDoctors) {
                boolean am = isDoctorOnLeave(doc.getId(), date, "上午");
                boolean pm = isDoctorOnLeave(doc.getId(), date, "下午");
                if (am && pm) leaveDoctorCount++;
            }

            long valid = allDoctors.size() - leaveDoctorCount;

            long total = appointmentMapper.selectCount(Wrappers.lambdaQuery(Appointment.class)
                    .eq(Appointment::getDeptId, deptId)
                    .eq(Appointment::getAppointDate, date)
                    .eq(Appointment::getStatus, 0));

            int max = (int) (valid * MAX_PER_PERIOD * 2);
            boolean isFull = total >= max;

            DayStatusVO vo = new DayStatusVO();
            vo.setDate(date);
            vo.setFull(isFull);
            vo.setRemaining(max - (int) total);
            list.add(vo);
        }

        return Result.ok(list);
    }

    // ====================== 时段可约状态（修复+过滤请假医生） ======================
    @Override
    public Result getPeriodStatus(Integer deptId, LocalDate date, String period) {
        List<Doctor> allDoctors = doctorMapper.selectList(Wrappers.lambdaQuery(Doctor.class)
                .eq(Doctor::getDepartmentId, deptId)
                .eq(Doctor::getStatus, 1));

        int validDoctorCount = 0;
        for (Doctor doc : allDoctors) {
            if (!isDoctorOnLeave(doc.getId(), date, period)) {
                validDoctorCount++;
            }
        }

        int max = validDoctorCount * MAX_PER_PERIOD;
        long total = appointmentMapper.selectCount(Wrappers.lambdaQuery(Appointment.class)
                .eq(Appointment::getDeptId, deptId)
                .eq(Appointment::getAppointDate, date)
                .eq(Appointment::getTimePeriod, period)
                .eq(Appointment::getStatus, 0));

        boolean canAppoint = total < max;

        PeriodStatusVO vo = new PeriodStatusVO();
        vo.setPeriod(period);
        vo.setCanAppoint(canAppoint);
        vo.setRemaining(max - (int) total);

        return Result.ok(vo);
    }

    // ====================== 我的预约 ======================
// ====================== 我的预约 ======================
    @Override
    public List<Appointment> getMyAppointments(Integer patientId) {
        return appointmentMapper.selectList(Wrappers.lambdaQuery(Appointment.class)
                .eq(Appointment::getPatientId, patientId)
                .orderByDesc(Appointment::getAppointDate));
    }

    // ====================== 【医生端】查询自己的预约 ======================
    @Override
    public List<Appointment> getByDoctor(Integer doctorId) {
        // 只返回待就诊的预约，避免医生看到已取消的记录
        return appointmentMapper.selectList(Wrappers.lambdaQuery(Appointment.class)
                .eq(Appointment::getDoctorId, doctorId)
                .eq(Appointment::getStatus, 0)  // 0-待就诊
                .orderByAsc(Appointment::getAppointDate)
                .orderByAsc(Appointment::getTimePeriod));
    }
    // ====================== 【公共】判断医生是否请假 ======================
    @Override
    public boolean isDoctorOnLeave(Integer doctorId, LocalDate date, String period) {
        return doctorLeaveService.isOnLeave(doctorId, date, period);
    }
}
package Service.Impl;

import Dto.Result;
import Event.MessageEvent;
import Mapper.AppointmentMapper;
import Mapper.DoctorLeaveMapper;
import Mapper.DoctorMapper;
import Mapper.PatientLimitMapper;
import PoJo.Appointment;
import PoJo.Doctor;
import PoJo.DoctorLeave;
import PoJo.PatientLimit;
import Service.DoctorLeaveService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import constant.AppointmentStatus;
import constant.RedisKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DoctorLeaveServiceImpl implements DoctorLeaveService {

    private final DoctorLeaveMapper doctorLeaveMapper;
    private final AppointmentMapper appointmentMapper;
    private final PatientLimitMapper patientLimitMapper;
    private final DoctorMapper doctorMapper;
    @Autowired
    private RedissonClient redissonClient;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final ApplicationContext applicationContext;

    // ====================== 常规请假（支持日期范围） ======================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result normalLeave(DoctorLeave leave, Integer doctorId) {
        Doctor doctor = doctorMapper.selectById(doctorId);
        if (doctor == null) return Result.fail("医生不存在");

        LocalDate startDate = leave.getLeaveDate();
        LocalDate endDate = leave.getEndDate() != null ? leave.getEndDate() : startDate;
        String period = leave.getTimePeriod() != null ? leave.getTimePeriod() : "全天";

        // 校验日期范围
        LocalDate now = LocalDate.now();
        if (startDate.isBefore(now)) return Result.fail("不能请假过去的日期");
        if (startDate.isBefore(now.plusDays(7))) return Result.fail("常规请假必须提前至少7天");
        if (endDate.isAfter(now.plusDays(30))) return Result.fail("最多只能请未来30天");
        if (endDate.isBefore(startDate)) return Result.fail("结束日期不能早于开始日期");

        // 遍历日期范围，逐天创建请假记录
        List<LocalDate> dateRange = getDateRange(startDate, endDate);
        List<String> periods = expandPeriods(period);

        for (LocalDate date : dateRange) {
            for (String p : periods) {
                String lockKey = "leave_lock:" + doctorId + ":" + date + ":" + p;
                RLock lock = redissonClient.getLock(lockKey);
                lock.lock();
                try {
                    DoctorLeave singleLeave = new DoctorLeave();
                    singleLeave.setDoctorId(doctorId);
                    singleLeave.setDeptId(doctor.getDepartmentId());
                    singleLeave.setLeaveDate(date);
                    singleLeave.setTimePeriod(p);
                    singleLeave.setType(1);
                    singleLeave.setStatus(1);
                    singleLeave.setReason(leave.getReason());

                    Long count = doctorLeaveMapper.selectCount(Wrappers.lambdaQuery(DoctorLeave.class)
                            .eq(DoctorLeave::getDoctorId, doctorId)
                            .eq(DoctorLeave::getLeaveDate, date)
                            .eq(DoctorLeave::getTimePeriod, p)
                            .eq(DoctorLeave::getStatus, 1));

                    if (count == null || count == 0) {
                        doctorLeaveMapper.insert(singleLeave);
                    }
                } finally {
                    if (lock.isHeldByCurrentThread()) lock.unlock();
                }
            }
        }
        log.info("常规请假成功: 医生{}, 日期{}~{}, 时段{}", doctorId, startDate, endDate, period);
        return Result.ok("请假成功");
    }

    // ====================== 紧急请假（支持日期范围，且自动取消已预约患者） ======================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result emergencyLeave(DoctorLeave leave, Integer doctorId) {
        Doctor doctor = doctorMapper.selectById(doctorId);
        if (doctor == null) return Result.fail("医生不存在");

        LocalDate startDate = leave.getLeaveDate();
        LocalDate endDate = leave.getEndDate() != null ? leave.getEndDate() : startDate;
        String period = leave.getTimePeriod() != null ? leave.getTimePeriod() : "全天";

        LocalDate now = LocalDate.now();
        if (startDate.isBefore(now)) return Result.fail("不能请假过去的日期");
        if (endDate.isAfter(now.plusDays(7))) return Result.fail("紧急请假仅限未来7天内");
        if (endDate.isBefore(startDate)) return Result.fail("结束日期不能早于开始日期");

        List<LocalDate> dateRange = getDateRange(startDate, endDate);
        List<String> periods = expandPeriods(period);

        for (LocalDate date : dateRange) {
            for (String p : periods) {
                String lockKey = "leave_lock:" + doctorId + ":" + date + ":" + p;
                RLock lock = redissonClient.getLock(lockKey);
                lock.lock();
                try {
                    DoctorLeave singleLeave = new DoctorLeave();
                    singleLeave.setDoctorId(doctorId);
                    singleLeave.setDeptId(doctor.getDepartmentId());
                    singleLeave.setLeaveDate(date);
                    singleLeave.setTimePeriod(p);
                    singleLeave.setType(2);
                    singleLeave.setStatus(1);
                    singleLeave.setReason(leave.getReason());

                    Long count = doctorLeaveMapper.selectCount(Wrappers.lambdaQuery(DoctorLeave.class)
                            .eq(DoctorLeave::getDoctorId, doctorId)
                            .eq(DoctorLeave::getLeaveDate, date)
                            .eq(DoctorLeave::getTimePeriod, p)
                            .eq(DoctorLeave::getStatus, 1));

                    if (count == null || count == 0) {
                        doctorLeaveMapper.insert(singleLeave);
                    }

                    // 自动取消该时段的患者预约
                    cancelAppointments(doctorId, date, p);
                } finally {
                    if (lock.isHeldByCurrentThread()) lock.unlock();
                }
            }
        }
        log.info("紧急请假成功: 医生{}, 日期{}~{}, 时段{}, 已取消期间预约", doctorId, startDate, endDate, period);
        return Result.ok("紧急请假成功，已自动取消期间预约");
    }

    // ====================== 取消预约（修复：使用正确的状态常量） ======================
    private void cancelAppointments(Integer doctorId, LocalDate date, String period) {
        List<Appointment> list = appointmentMapper.selectList(Wrappers.lambdaQuery(Appointment.class)
                .eq(Appointment::getDoctorId, doctorId)
                .eq(Appointment::getAppointDate, date)
                .eq(Appointment::getTimePeriod, period)
                .eq(Appointment::getStatus, 0));

        if (list == null || list.isEmpty()) {
            log.info("日期{}时段{}无待就诊预约，无需取消", date, period);
            return;
        }

        List<MessageEvent> eventList = new ArrayList<>();

        for (Appointment appoint : list) {
            // 修复: 使用 AppointmentStatus.DOCTOR_LEAVE_CANCEL (5) 而非硬编码的3
            appoint.setStatus(AppointmentStatus.DOCTOR_LEAVE_CANCEL);
            appointmentMapper.updateById(appoint);

            MessageEvent event = new MessageEvent(
                    this,
                    Long.valueOf(appoint.getPatientId()),
                    1,
                    "预约已取消（医生请假）",
                    "抱歉，您预约的医生于" + date + " " + period + "临时请假，预约已自动取消，请重新预约",
                    5,
                    Long.valueOf(appoint.getId())
            );
            eventList.add(event);

            // 恢复患者预约次数
            PatientLimit limit = patientLimitMapper.selectOne(Wrappers.lambdaQuery(PatientLimit.class)
                    .eq(PatientLimit::getPatientId, appoint.getPatientId())
                    .eq(PatientLimit::getDate, date));

            if (limit != null && limit.getAppointCount() > 0) {
                limit.setAppointCount(limit.getAppointCount() - 1);
                patientLimitMapper.updateById(limit);
            }
        }

        // 批量推入Redis队列（手动序列化，确保与 MessageConsumeJob 兼容）
        for (MessageEvent event : eventList) {
            try {
                String json = objectMapper.writeValueAsString(event);
                stringRedisTemplate.opsForList().leftPush(RedisKey.MESSAGE_QUEUE, json);
            } catch (Exception e) {
                log.error("请假取消消息序列化失败: userId={}", event.userId, e);
            }
        }

        log.info("取消预约完成，日期:{}, 时段:{}, 共取消:{}条", date, period, list.size());
    }

    // ====================== 取消请假 ======================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result cancelLeave(Integer id, Integer doctorId) {
        String lockKey = "cancel_leave_lock:" + id;
        RLock lock = redissonClient.getLock(lockKey);
        lock.lock();
        try {
            DoctorLeave leave = doctorLeaveMapper.selectById(id);
            if (leave == null) return Result.fail("记录不存在");
            if (!leave.getDoctorId().equals(doctorId)) return Result.fail("无权限");
            if (leave.getStatus() != 1) return Result.fail("已取消，无需重复操作");
            if (leave.getLeaveDate().isBefore(LocalDate.now())) return Result.fail("不能取消过去的请假");

            leave.setStatus(0);
            doctorLeaveMapper.updateById(leave);
            return Result.ok("取消成功");
        } finally {
            lock.unlock();
        }
    }

    // ====================== 我的请假 ======================
    @Override
    public List<DoctorLeave> myLeaveList(Integer doctorId) {
        return doctorLeaveMapper.selectList(Wrappers.lambdaQuery(DoctorLeave.class)
                .eq(DoctorLeave::getDoctorId, doctorId)
                .orderByDesc(DoctorLeave::getLeaveDate));
    }

    // ====================== 判断医生是否请假 ======================
    @Override
    public boolean isOnLeave(Integer doctorId, LocalDate date, String period) {
        Long count = doctorLeaveMapper.selectCount(Wrappers.lambdaQuery(DoctorLeave.class)
                .eq(DoctorLeave::getDoctorId, doctorId)
                .eq(DoctorLeave::getLeaveDate, date)
                .eq(DoctorLeave::getStatus, 1)
                .and(w -> w.eq(DoctorLeave::getTimePeriod, period)
                        .or().eq(DoctorLeave::getTimePeriod, "全天")));
        return count > 0;
    }

    // ====================== 私有工具方法 ======================
    /** 获取日期范围内的所有日期 */
    private List<LocalDate> getDateRange(LocalDate start, LocalDate end) {
        long days = ChronoUnit.DAYS.between(start, end);
        List<LocalDate> dates = new ArrayList<>();
        for (long i = 0; i <= days; i++) {
            dates.add(start.plusDays(i));
        }
        return dates;
    }

    /** 展开时段：全天 → [上午, 下午] */
    private List<String> expandPeriods(String period) {
        if ("全天".equals(period)) {
            return List.of("上午", "下午");
        }
        return List.of(period);
    }
}

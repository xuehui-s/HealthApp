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
import constant.RedisKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DoctorLeaveServiceImpl implements DoctorLeaveService {

    private final DoctorLeaveMapper doctorLeaveMapper;
    private final AppointmentMapper appointmentMapper;
    private final PatientLimitMapper patientLimitMapper;
    private final DoctorMapper doctorMapper;
    @Autowired
    private  RedissonClient redissonClient;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private  final ApplicationContext applicationContext;

    // ====================== 常规请假 ======================
    @Override
    public Result normalLeave(DoctorLeave leave, Integer doctorId) {
        String lockKey = "leave_lock:" + doctorId + ":" + leave.getLeaveDate() + ":" + leave.getTimePeriod();
        RLock lock = redissonClient.getLock(lockKey);
        lock.lock();
        try {
            Doctor doctor = doctorMapper.selectById(doctorId);
            if (doctor == null) {
                return Result.fail("医生不存在");
            }

            LocalDate now = LocalDate.now();
            if (leave.getLeaveDate().isBefore(now)) {
                return Result.fail("不能请假过去的日期");
            }
            if (leave.getLeaveDate().isBefore(now.plusDays(7))) {
                return Result.fail("常规请假必须提前至少7天");
            }
            if (leave.getLeaveDate().isAfter(now.plusDays(30))) {
                return Result.fail("最多只能请未来30天");
            }

            return saveLeaveWithDoctor(leave, doctor, 1);
        } finally {
            lock.unlock();
        }
    }

    // ====================== 紧急请假 ======================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result emergencyLeave(DoctorLeave leave, Integer doctorId) {
        String lockKey = "leave_lock:" + doctorId + ":" + leave.getLeaveDate() + ":" + leave.getTimePeriod();
        RLock lock = redissonClient.getLock(lockKey);
        lock.lock();
        try {
            Doctor doctor = doctorMapper.selectById(doctorId);
            if (doctor == null) {
                return Result.fail("医生不存在");
            }

            LocalDate now = LocalDate.now();
            if (leave.getLeaveDate().isBefore(now)) {
                return Result.fail("不能请假过去的日期");
            }
            if (leave.getLeaveDate().isAfter(now.plusDays(7))) {
                return Result.fail("紧急请假仅限未来7天内");
            }

            Result result = saveLeaveWithDoctor(leave, doctor, 2);
            if (!result.getSuccess()) {
                return result;
            }

            cancelAppointments(doctorId, leave.getLeaveDate(), leave.getTimePeriod());
            return result;
        } finally {
            lock.unlock();
        }
    }

    // ====================== 保存请假 ======================
    private Result saveLeaveWithDoctor(DoctorLeave leave, Doctor doctor, int type) {
        Integer doctorId = doctor.getId();
        leave.setDoctorId(doctorId);
        leave.setDeptId(doctor.getDepartmentId());
        leave.setType(type);
        leave.setStatus(1);

        // 防重复请假
        Long count = doctorLeaveMapper.selectCount(Wrappers.lambdaQuery(DoctorLeave.class)
                .eq(DoctorLeave::getDoctorId, doctorId)
                .eq(DoctorLeave::getLeaveDate, leave.getLeaveDate())
                .eq(DoctorLeave::getTimePeriod, leave.getTimePeriod())
                .eq(DoctorLeave::getStatus, 1));

        if (count != null && count > 0) {
            return Result.fail("该时段已请假，请勿重复提交");
        }

        doctorLeaveMapper.insert(leave);
        return Result.ok("请假成功");
    }

    // ====================== 取消预约（已修复BUG） ======================
    private void cancelAppointments(Integer doctorId, LocalDate date, String period) {
        List<Appointment> list = appointmentMapper.selectList(Wrappers.lambdaQuery(Appointment.class)
                .eq(Appointment::getDoctorId, doctorId)
                .eq(Appointment::getAppointDate, date)
                .eq(Appointment::getTimePeriod, period)
                .eq(Appointment::getStatus, 0));

        if (list == null || list.isEmpty()) {
            log.info("无待就诊预约，无需取消");
            return;
        }

        List<MessageEvent> eventList = new ArrayList<>();

        for (Appointment appoint : list) {
            appoint.setStatus(3);
            appointmentMapper.updateById(appoint);

            // 构建消息
            MessageEvent event = new MessageEvent(
                    this,
                    Long.valueOf(appoint.getPatientId()),
                    1,
                    "预约已取消（医生请假）",
                    "抱歉，您预约的医生临时请假，预约已自动取消",
                    5,
                    Long.valueOf(appoint.getId())
            );

            eventList.add(event);

            // 恢复次数
            PatientLimit limit = patientLimitMapper.selectOne(Wrappers.lambdaQuery(PatientLimit.class)
                    .eq(PatientLimit::getPatientId, appoint.getPatientId())
                    .eq(PatientLimit::getDate, date));

            if (limit != null && limit.getAppointCount() > 0) {
                limit.setAppointCount(limit.getAppointCount() - 1);
                patientLimitMapper.updateById(limit);
            }
        }

        // 批量推入Redis（高并发安全）
        for (MessageEvent event : eventList) {
            redisTemplate.opsForList().leftPush(RedisKey.MESSAGE_QUEUE, event);
        }

        log.info("取消预约完成，共取消：{} 条，消息已全部推送", list.size());
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
            if (leave == null) {
                return Result.fail("记录不存在");
            }
            if (!leave.getDoctorId().equals(doctorId)) {
                return Result.fail("无权限");
            }
            if (leave.getStatus() != 1) {
                return Result.fail("已取消，无需重复操作");
            }
            if (leave.getLeaveDate().isBefore(LocalDate.now())) {
                return Result.fail("不能取消过去的请假");
            }

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
}
package it.guowei.healthapp.service.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import it.guowei.healthapp.common.exception.BusinessException;
import it.guowei.healthapp.common.result.PageResult;
import it.guowei.healthapp.common.result.ResultCode;
import it.guowei.healthapp.domain.entity.AiConversation;
import it.guowei.healthapp.domain.entity.OperationLog;
import it.guowei.healthapp.infrastructure.mapper.AiConversationMapper;
import it.guowei.healthapp.infrastructure.mapper.OperationLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import Mapper.AppointmentMapper;
import Mapper.DepartmentMapper;
import Mapper.DoctorMapper;
import Mapper.PatientLimitMapper;
import Mapper.PatientMapper;
import Mapper.PayOrderMapper;
import Mapper.RefundOrderMapper;
import PoJo.Appointment;
import PoJo.Department;
import PoJo.Doctor;
import PoJo.Patient;
import PoJo.PatientLimit;
import PoJo.PayOrder;
import PoJo.RefundOrder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 管理端综合管理服务
 * 数据看板之外的管理能力：患者/医生/科室/预约/缴费单/退款/AI对话/操作日志
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminManageService {

    private final PatientMapper patientMapper;
    private final DoctorMapper doctorMapper;
    private final DepartmentMapper departmentMapper;
    private final AppointmentMapper appointmentMapper;
    private final PayOrderMapper payOrderMapper;
    private final RefundOrderMapper refundOrderMapper;
    private final PatientLimitMapper patientLimitMapper;
    private final AiConversationMapper aiConversationMapper;
    private final OperationLogMapper operationLogMapper;

    // ==================== 患者管理 ====================
    public PageResult<Patient> pagePatients(int page, int size, String keyword) {
        LambdaQueryWrapper<Patient> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            qw.and(w -> w.like(Patient::getName, keyword)
                    .or().like(Patient::getUsername, keyword)
                    .or().like(Patient::getPhone, keyword));
        }
        qw.orderByDesc(Patient::getId);
        Page<Patient> p = patientMapper.selectPage(new Page<>(page, size), qw);
        p.getRecords().forEach(d -> d.setPassword(null)); // 脱敏
        return PageResult.of(p.getRecords(), p.getTotal(), page, size);
    }

    public void updatePatientStatus(Long id, Integer status) {
        Patient patient = patientMapper.selectById(id);
        if (patient == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        Patient update = new Patient();
        update.setId(id.intValue());
        update.setStatus(status);
        patientMapper.updateById(update);
    }

    // ==================== 医生管理 ====================
    public PageResult<Doctor> pageDoctors(int page, int size, Long deptId, String keyword) {
        LambdaQueryWrapper<Doctor> qw = new LambdaQueryWrapper<>();
        if (deptId != null) {
            qw.eq(Doctor::getDepartmentId, deptId.intValue());
        }
        if (StringUtils.hasText(keyword)) {
            qw.and(w -> w.like(Doctor::getName, keyword)
                    .or().like(Doctor::getUsername, keyword)
                    .or().like(Doctor::getPhone, keyword));
        }
        qw.orderByDesc(Doctor::getId);
        Page<Doctor> p = doctorMapper.selectPage(new Page<>(page, size), qw);
        List<Doctor> records = p.getRecords();
        // 密码/身份证脱敏
        records.forEach(d -> {
            d.setPassword(null);
            d.setIdCard(null);
        });
        return PageResult.of(records, p.getTotal(), page, size);
    }

    public void updateDoctorStatus(Long id, Integer status) {
        Doctor doctor = doctorMapper.selectById(id);
        if (doctor == null) {
            throw new BusinessException(ResultCode.DOCTOR_NOT_FOUND);
        }
        Doctor update = new Doctor();
        update.setId(id.intValue());
        update.setStatus(status);
        doctorMapper.updateById(update);
    }

    // ==================== 科室管理 ====================
    public List<Department> listDepartments() {
        return departmentMapper.selectList(new LambdaQueryWrapper<Department>().orderByAsc(Department::getId));
    }

    public void createDepartment(Department dept) {
        Long count = departmentMapper.selectCount(new LambdaQueryWrapper<Department>()
                .eq(Department::getName, dept.getName()));
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.DEPARTMENT_NOT_FOUND, "科室名称已存在");
        }
        if (dept.getStatus() == null) {
            dept.setStatus(1);
        }
        departmentMapper.insert(dept);
    }

    public void updateDepartment(Long id, Department dept) {
        Department update = new Department();
        update.setId(id.intValue());
        update.setName(dept.getName());
        update.setDescription(dept.getDescription());
        departmentMapper.updateById(update);
    }

    public void updateDepartmentStatus(Long id, Integer status) {
        Department update = new Department();
        update.setId(id.intValue());
        update.setStatus(status);
        departmentMapper.updateById(update);
    }

    // ==================== 预约管理 ====================
    public PageResult<Map<String, Object>> pageAppointments(int page, int size,
                                                            Integer status, LocalDate date, Long deptId) {
        LambdaQueryWrapper<Appointment> qw = new LambdaQueryWrapper<>();
        if (status != null) {
            qw.eq(Appointment::getStatus, status);
        }
        if (date != null) {
            qw.eq(Appointment::getAppointDate, date);
        }
        if (deptId != null) {
            qw.eq(Appointment::getDeptId, deptId.intValue());
        }
        qw.orderByDesc(Appointment::getId);
        Page<Appointment> p = appointmentMapper.selectPage(new Page<>(page, size), qw);

        // 批量补充 患者姓名/医生姓名/科室名（避免 N+1 查询）
        Map<Integer, String> patientNames = patientNamesByIds(
                p.getRecords().stream().map(Appointment::getPatientId).filter(Objects::nonNull).toList());
        Map<Integer, String> doctorNames = doctorNamesByIds(
                p.getRecords().stream().map(Appointment::getDoctorId).filter(Objects::nonNull).toList());
        Map<Integer, String> deptNames = departmentMap();

        List<Map<String, Object>> records = p.getRecords().stream().map(a -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", a.getId());
            m.put("patientId", a.getPatientId());
            m.put("patientName", patientNames.get(a.getPatientId()));
            m.put("doctorId", a.getDoctorId());
            m.put("doctorName", doctorNames.get(a.getDoctorId()));
            m.put("deptId", a.getDeptId());
            m.put("deptName", deptNames.get(a.getDeptId()));
            m.put("appointDate", String.valueOf(a.getAppointDate()));
            m.put("timePeriod", a.getTimePeriod());
            m.put("queueNum", a.getQueueNum());
            m.put("status", a.getStatus());
            m.put("createTime", a.getCreateTime());
            return m;
        }).toList();
        return PageResult.of(records, p.getTotal(), page, size);
    }

    /** 管理员强制取消（患者侧规则如当日取消上限不适用于管理端） */
    @Transactional
    public void adminCancelAppointment(Long id) {
        Appointment appt = appointmentMapper.selectById(id);
        if (appt == null) {
            throw new BusinessException(ResultCode.APPOINTMENT_NOT_FOUND);
        }
        if (appt.getStatus() != 0) {
            throw new BusinessException(ResultCode.APPOINTMENT_NOT_FOUND, "仅待就诊状态的预约可取消");
        }
        Appointment update = new Appointment();
        update.setId(id.intValue());
        update.setStatus(4); // 患者取消
        appointmentMapper.updateById(update);
        // 归还当日预约名额
        PatientLimit limit = patientLimitMapper.selectOne(new LambdaQueryWrapper<PatientLimit>()
                .eq(PatientLimit::getPatientId, appt.getPatientId())
                .eq(PatientLimit::getDate, appt.getAppointDate()));
        if (limit != null && limit.getAppointCount() != null && limit.getAppointCount() > 0) {
            PatientLimit upd = new PatientLimit();
            upd.setId(limit.getId());
            upd.setAppointCount(limit.getAppointCount() - 1);
            patientLimitMapper.updateById(upd);
        }
        log.info("管理员取消预约: id={}, patient={}", id, appt.getPatientId());
    }

    // ==================== 缴费订单 ====================
    public PageResult<PayOrder> pagePayOrders(int page, int size, Integer status, String orderNo) {
        LambdaQueryWrapper<PayOrder> qw = new LambdaQueryWrapper<>();
        if (status != null) {
            qw.eq(PayOrder::getStatus, status);
        }
        if (StringUtils.hasText(orderNo)) {
            qw.like(PayOrder::getOrderNo, orderNo);
        }
        qw.orderByDesc(PayOrder::getId);
        Page<PayOrder> p = payOrderMapper.selectPage(new Page<>(page, size), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), page, size);
    }

    public PageResult<RefundOrder> pageRefunds(int page, int size, Integer status) {
        LambdaQueryWrapper<RefundOrder> qw = new LambdaQueryWrapper<>();
        if (status != null) {
            qw.eq(RefundOrder::getStatus, status);
        }
        qw.orderByDesc(RefundOrder::getId);
        Page<RefundOrder> p = refundOrderMapper.selectPage(new Page<>(page, size), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), page, size);
    }

    // ==================== AI 对话记录 ====================
    public PageResult<AiConversation> pageAiConversations(int page, int size, String sessionId, String role) {
        LambdaQueryWrapper<AiConversation> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(sessionId)) {
            qw.eq(AiConversation::getSessionId, sessionId);
        }
        if (StringUtils.hasText(role)) {
            qw.eq(AiConversation::getRole, role);
        }
        qw.orderByDesc(AiConversation::getId);
        Page<AiConversation> p = aiConversationMapper.selectPage(new Page<>(page, size), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), page, size);
    }

    // ==================== 操作日志 ====================
    public PageResult<OperationLog> pageOperationLogs(int page, int size, String module, String username) {
        LambdaQueryWrapper<OperationLog> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(module)) {
            qw.eq(OperationLog::getModule, module);
        }
        if (StringUtils.hasText(username)) {
            qw.like(OperationLog::getUsername, username);
        }
        qw.orderByDesc(OperationLog::getId);
        Page<OperationLog> p = operationLogMapper.selectPage(new Page<>(page, size), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), page, size);
    }

    // ==================== 私有辅助 ====================
    private Map<Integer, String> departmentMap() {
        return departmentMapper.selectList(null).stream()
                .collect(Collectors.toMap(Department::getId, Department::getName, (a, b) -> a));
    }

    private Map<Integer, String> patientNamesByIds(List<Integer> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return patientMapper.selectBatchIds(ids).stream()
                .filter(pt -> pt.getName() != null || pt.getUsername() != null)
                .collect(Collectors.toMap(Patient::getId,
                        pt -> pt.getName() != null ? pt.getName() : pt.getUsername(), (a, b) -> a));
    }

    private Map<Integer, String> doctorNamesByIds(List<Integer> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return doctorMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Doctor::getId, Doctor::getName, (a, b) -> a));
    }
}

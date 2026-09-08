package it.guowei.healthapp.controller.admin;

import it.guowei.healthapp.common.annotation.OperationLog;
import it.guowei.healthapp.common.context.UserContext;
import it.guowei.healthapp.common.exception.BusinessException;
import it.guowei.healthapp.common.result.PageResult;
import it.guowei.healthapp.common.result.Result;
import it.guowei.healthapp.common.result.ResultCode;
import it.guowei.healthapp.domain.entity.AiConversation;
import it.guowei.healthapp.service.admin.AdminManageService;
import it.guowei.healthapp.service.admin.AdminStatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import Service.PayOrderService;
import PoJo.PayOrder;
import PoJo.RefundOrder;
import PoJo.Department;
import PoJo.Doctor;
import PoJo.Patient;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 管理端综合管理接口（/api/v1/admin/**，需管理员身份）
 * 覆盖：患者 / 医生 / 科室 / 预约 / 缴费单 / 退款审核 / AI对话 / 操作日志
 */
@Tag(name = "管理端-综合管理", description = "患者、医生、科室、预约、缴费、退款、审计")
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminManageController {

    private final AdminManageService manageService;
    private final AdminStatsService statsService;
    private final PayOrderService payOrderService;

    // ==================== 数据看板 ====================
    @Operation(summary = "仪表盘概览")
    @GetMapping("/stats/dashboard")
    public Result<Map<String, Object>> dashboard() {
        return Result.ok(statsService.getDashboardOverview());
    }

    @Operation(summary = "预约趋势")
    @GetMapping("/stats/appointment-trend")
    public Result<List<Map<String, Object>>> appointmentTrend(@RequestParam(defaultValue = "7") int days) {
        return Result.ok(statsService.getAppointmentTrend(days));
    }

    @Operation(summary = "营收趋势")
    @GetMapping("/stats/revenue-trend")
    public Result<List<Map<String, Object>>> revenueTrend(@RequestParam(defaultValue = "7") int days) {
        return Result.ok(statsService.getRevenueTrend(days));
    }

    @Operation(summary = "科室预约排行")
    @GetMapping("/stats/department-ranking")
    public Result<List<Map<String, Object>>> departmentRanking() {
        return Result.ok(statsService.getDepartmentRanking());
    }

    @Operation(summary = "医生工作量排行")
    @GetMapping("/stats/doctor-workload")
    public Result<List<Map<String, Object>>> doctorWorkload() {
        return Result.ok(statsService.getDoctorWorkloadRanking());
    }

    @Operation(summary = "支付方式分布")
    @GetMapping("/stats/pay-method-distribution")
    public Result<List<Map<String, Object>>> payMethodDistribution() {
        return Result.ok(statsService.getPayMethodDistribution());
    }

    // ==================== 患者管理 ====================
    @Operation(summary = "患者分页列表")
    @GetMapping("/patients")
    public Result<PageResult<Patient>> patients(@RequestParam(defaultValue = "1") int page,
                                                @RequestParam(defaultValue = "10") int size,
                                                @RequestParam(required = false) String keyword) {
        return Result.ok(manageService.pagePatients(page, size, keyword));
    }

    @Operation(summary = "启用/禁用患者")
    @PutMapping("/patients/{id}/status")
    @OperationLog(module = "患者管理", description = "变更患者状态", type = "UPDATE")
    public Result<Void> patientStatus(@PathVariable Long id, @RequestParam Integer status) {
        manageService.updatePatientStatus(id, status);
        return Result.ok();
    }

    // ==================== 医生管理 ====================
    @Operation(summary = "医生分页列表")
    @GetMapping("/doctors")
    public Result<PageResult<Doctor>> doctors(@RequestParam(defaultValue = "1") int page,
                                              @RequestParam(defaultValue = "10") int size,
                                              @RequestParam(required = false) Long deptId,
                                              @RequestParam(required = false) String keyword) {
        return Result.ok(manageService.pageDoctors(page, size, deptId, keyword));
    }

    @Operation(summary = "启用/停诊医生")
    @PutMapping("/doctors/{id}/status")
    @OperationLog(module = "医生管理", description = "变更医生状态", type = "UPDATE")
    public Result<Void> doctorStatus(@PathVariable Long id, @RequestParam Integer status) {
        manageService.updateDoctorStatus(id, status);
        return Result.ok();
    }

    // ==================== 科室管理 ====================
    @Operation(summary = "科室列表")
    @GetMapping("/departments")
    public Result<List<Department>> departments() {
        return Result.ok(manageService.listDepartments());
    }

    @Operation(summary = "新增科室")
    @PostMapping("/departments")
    @OperationLog(module = "科室管理", description = "新增科室", type = "CREATE")
    public Result<Void> addDepartment(@RequestBody Department dept) {
        manageService.createDepartment(dept);
        return Result.ok();
    }

    @Operation(summary = "编辑科室")
    @PutMapping("/departments/{id}")
    @OperationLog(module = "科室管理", description = "编辑科室", type = "UPDATE")
    public Result<Void> updateDepartment(@PathVariable Long id, @RequestBody Department dept) {
        manageService.updateDepartment(id, dept);
        return Result.ok();
    }

    @Operation(summary = "启用/停用科室")
    @PutMapping("/departments/{id}/status")
    @OperationLog(module = "科室管理", description = "变更科室状态", type = "UPDATE")
    public Result<Void> departmentStatus(@PathVariable Long id, @RequestParam Integer status) {
        manageService.updateDepartmentStatus(id, status);
        return Result.ok();
    }

    // ==================== 预约管理 ====================
    @Operation(summary = "预约分页列表")
    @GetMapping("/appointments")
    public Result<PageResult<Map<String, Object>>> appointments(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Long deptId) {
        return Result.ok(manageService.pageAppointments(page, size, status, date, deptId));
    }

    @Operation(summary = "管理员取消预约")
    @PostMapping("/appointments/{id}/cancel")
    @OperationLog(module = "预约管理", description = "取消预约", type = "CANCEL")
    public Result<Void> cancelAppointment(@PathVariable Long id) {
        manageService.adminCancelAppointment(id);
        return Result.ok();
    }

    // ==================== 缴费订单 ====================
    @Operation(summary = "缴费单分页列表")
    @GetMapping("/pay-orders")
    public Result<PageResult<PayOrder>> payOrders(@RequestParam(defaultValue = "1") int page,
                                                  @RequestParam(defaultValue = "10") int size,
                                                  @RequestParam(required = false) Integer status,
                                                  @RequestParam(required = false) String orderNo) {
        return Result.ok(manageService.pagePayOrders(page, size, status, orderNo));
    }

    @Operation(summary = "缴费单详情（含明细）")
    @GetMapping("/pay-orders/{orderNo}/detail")
    public Result<Object> payOrderDetail(@PathVariable String orderNo) {
        Dto.Result r = payOrderService.getOrderDetail(orderNo);
        if (!Boolean.TRUE.equals(r.getSuccess())) {
            throw new BusinessException(ResultCode.ORDER_NOT_FOUND, r.getErrorMsg());
        }
        return Result.ok(r.getData());
    }

    // ==================== 退款审核 ====================
    @Operation(summary = "退款单分页列表")
    @GetMapping("/refunds")
    public Result<PageResult<RefundOrder>> refunds(@RequestParam(defaultValue = "1") int page,
                                                   @RequestParam(defaultValue = "10") int size,
                                                   @RequestParam(required = false) Integer status) {
        return Result.ok(manageService.pageRefunds(page, size, status));
    }

    @Operation(summary = "退款审核（通过/拒绝）")
    @PostMapping("/refunds/{id}/audit")
    @OperationLog(module = "退款审核", description = "审核退款单", type = "AUDIT")
    public Result<Object> auditRefund(@PathVariable Long id,
                                      @RequestParam Integer auditResult,
                                      @RequestParam(required = false) String auditRemark) {
        Long adminId = UserContext.getUserId();
        Dto.Result r = payOrderService.auditRefund(id, auditResult, auditRemark, adminId);
        if (!Boolean.TRUE.equals(r.getSuccess())) {
            throw new BusinessException(ResultCode.REFUND_NOT_ALLOWED, r.getErrorMsg());
        }
        return Result.ok(r.getData());
    }

    // ==================== AI 对话审计 ====================
    @Operation(summary = "AI对话记录分页")
    @GetMapping("/ai/conversations")
    public Result<PageResult<AiConversation>> aiConversations(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String sessionId,
            @RequestParam(required = false) String role) {
        return Result.ok(manageService.pageAiConversations(page, size, sessionId, role));
    }

    // ==================== 操作日志 ====================
    @Operation(summary = "操作日志分页")
    @GetMapping("/logs")
    public Result<PageResult<it.guowei.healthapp.domain.entity.OperationLog>> logs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String username) {
        return Result.ok(manageService.pageOperationLogs(page, size, module, username));
    }

    @Data
    public static class DepartmentRequest {
        private String name;
        private String description;
    }
}

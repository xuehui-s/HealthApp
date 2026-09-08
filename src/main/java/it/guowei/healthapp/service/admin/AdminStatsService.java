package it.guowei.healthapp.service.admin;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;

/**
 * 管理端数据统计服务
 * 提供仪表盘数据：预约量、营收、患者数、医生工作量等
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminStatsService {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 仪表盘概览数据
     */
    public Map<String, Object> getDashboardOverview() {
        Map<String, Object> result = new LinkedHashMap<>();
        LocalDate today = LocalDate.now();

        // 今日预约量
        Long todayAppointments = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM appointment WHERE appoint_date = ? AND deleted = 0",
                Long.class, today);
        result.put("todayAppointments", todayAppointments != null ? todayAppointments : 0);

        // 今日营收
        var todayRevenue = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(total_amount),0) FROM pay_order WHERE status = 1 AND DATE(pay_time) = ?",
                java.math.BigDecimal.class, today);
        result.put("todayRevenue", todayRevenue);

        // 总患者数
        Long totalPatients = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM patient WHERE deleted = 0", Long.class);
        result.put("totalPatients", totalPatients != null ? totalPatients : 0);

        // 总医生数
        Long totalDoctors = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM doctor WHERE status = 1 AND deleted = 0", Long.class);
        result.put("totalDoctors", totalDoctors != null ? totalDoctors : 0);

        // 待缴费订单数
        Long waitPayOrders = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pay_order WHERE status = 0 AND deleted = 0", Long.class);
        result.put("waitPayOrders", waitPayOrders != null ? waitPayOrders : 0);

        // 今日AI对话数
        Long todayAiChats = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ai_conversation WHERE DATE(create_time) = ?",
                Long.class, today);
        result.put("todayAiChats", todayAiChats != null ? todayAiChats : 0);

        return result;
    }

    /**
     * 近7天预约趋势
     */
    public List<Map<String, Object>> getAppointmentTrend(int days) {
        return jdbcTemplate.queryForList(
                "SELECT appoint_date as date, COUNT(*) as count " +
                "FROM appointment WHERE appoint_date >= DATE_SUB(CURDATE(), INTERVAL ? DAY) AND deleted = 0 " +
                "GROUP BY appoint_date ORDER BY appoint_date", days);
    }

    /**
     * 近7天营收趋势
     */
    public List<Map<String, Object>> getRevenueTrend(int days) {
        return jdbcTemplate.queryForList(
                "SELECT DATE(pay_time) as date, COALESCE(SUM(total_amount),0) as revenue " +
                "FROM pay_order WHERE status = 1 AND pay_time >= DATE_SUB(CURDATE(), INTERVAL ? DAY) " +
                "GROUP BY DATE(pay_time) ORDER BY date", days);
    }

    /**
     * 科室预约排行
     */
    public List<Map<String, Object>> getDepartmentRanking() {
        return jdbcTemplate.queryForList(
                "SELECT d.name as department, COUNT(a.id) as count " +
                "FROM appointment a LEFT JOIN department d ON a.dept_id = d.id " +
                "WHERE a.deleted = 0 GROUP BY a.dept_id ORDER BY count DESC LIMIT 10");
    }

    /**
     * 医生工作量排行
     */
    public List<Map<String, Object>> getDoctorWorkloadRanking() {
        return jdbcTemplate.queryForList(
                "SELECT doc.name as doctor, dept.name as department, COUNT(a.id) as patient_count " +
                "FROM appointment a LEFT JOIN doctor doc ON a.doctor_id = doc.id " +
                "LEFT JOIN department dept ON doc.department_id = dept.id " +
                "WHERE a.deleted = 0 AND a.status IN (1,2,3) " +
                "GROUP BY a.doctor_id ORDER BY patient_count DESC LIMIT 10");
    }

    /**
     * 支付方式分布
     */
    public List<Map<String, Object>> getPayMethodDistribution() {
        return jdbcTemplate.queryForList(
                "SELECT pay_method, COUNT(*) as count, COALESCE(SUM(total_amount),0) as amount " +
                "FROM pay_order WHERE status = 1 AND deleted = 0 GROUP BY pay_method");
    }
}

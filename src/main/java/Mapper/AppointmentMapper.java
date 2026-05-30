package Mapper;

import PoJo.Appointment;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;

@Mapper
public interface AppointmentMapper extends BaseMapper<Appointment> {
    // 统计医生某时段已预约数
    @Select("SELECT COUNT(*) FROM appointment WHERE doctor_id = #{doctorId} AND appoint_date = #{date} AND time_period = #{period} AND status = 0")
    Integer countByDoctorAndDatePeriod(
            @Param("doctorId") Integer doctorId,
            @Param("date") LocalDate date,
            @Param("period") String period);
}
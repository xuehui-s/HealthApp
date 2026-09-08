package PoJo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("doctor_leave")
public class DoctorLeave {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private Integer doctorId;
    private Integer deptId;
    private LocalDate leaveDate;
    private String timePeriod;
    private Integer type;      // 1=常规 2=紧急
    private Integer status;    // 1=生效 0=已取消
    private String reason;     // 请假原因
    private LocalDateTime createTime;

    // 以下字段不在数据库中，仅用于接收前端传参
    @TableField(exist = false)
    private LocalDate endDate;     // 结束日期（前端传入）
    @TableField(exist = false)
    private String leaveType;      // 请假类型字符串: normal/emergency
}

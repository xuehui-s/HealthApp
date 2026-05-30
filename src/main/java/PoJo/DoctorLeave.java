package PoJo;

import com.baomidou.mybatisplus.annotation.IdType;
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
    private LocalDateTime createTime;
}
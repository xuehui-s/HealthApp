package PoJo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("appointment")
public class Appointment {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private Integer patientId;
    private Integer deptId;
    private Integer doctorId;
    private LocalDate appointDate;
    private String timePeriod;
    private Integer queueNum;
    private Integer frontCount;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
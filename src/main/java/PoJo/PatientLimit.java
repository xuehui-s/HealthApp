package PoJo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;

@Data
@TableName("patient_limit")
public class PatientLimit {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private Integer patientId;
    private LocalDate date;
    private Integer appointCount;
    private Integer cancelCount;
}
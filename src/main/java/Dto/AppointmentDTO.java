package Dto;

import lombok.Data;
import java.time.LocalDate;

@Data
public class AppointmentDTO {
    private Integer deptId;
    private Integer doctorId;
    private LocalDate appointDate;
    private String timePeriod; // 上午/下午
}
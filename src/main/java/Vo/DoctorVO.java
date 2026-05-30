package Vo;

import lombok.Data;

@Data
public class DoctorVO {
    private Integer id;
    private String name;
    private Boolean onLeave; // true=请假中，前端置灰
}
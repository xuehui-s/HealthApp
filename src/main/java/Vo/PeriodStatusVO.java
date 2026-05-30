package Vo;

import lombok.Data;

@Data
public class PeriodStatusVO {
    private String period;       // 时段（上午/下午）
    private boolean canAppoint;  // 是否可预约
    private int remaining;       // 剩余号源
}

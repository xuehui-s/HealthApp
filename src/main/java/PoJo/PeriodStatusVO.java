package PoJo;

import lombok.Data;

@Data
public class PeriodStatusVO {
    private String period;    // 上午/下午
    private boolean canAppoint; // 是否可约
    private int remaining;    // 剩余号
}
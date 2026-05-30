package PoJo;

import lombok.Data;
import java.time.LocalDate;

@Data
public class DayStatusVO {
    private LocalDate date;
    private boolean isFull;    // 是否满员
    private int remaining;     // 剩余号源
}
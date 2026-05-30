package Vo;

import lombok.Data;
import java.time.LocalDate;

@Data
public class DayStatusVO {
    private LocalDate date;      // 日期
    private boolean isFull;      // 是否已满
    private int remaining;       // 剩余号源
}

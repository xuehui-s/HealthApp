package PoJo;

import lombok.Data;
import java.io.Serializable;

@Data
public class PayOrderDelayMsg implements Serializable {
    // 缴费单号
    private String orderNo;
    // 关联预约ID
    private Long appointmentId;
}
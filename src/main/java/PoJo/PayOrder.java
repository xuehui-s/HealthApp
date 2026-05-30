package PoJo;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.util.Date;

@Data
@TableName("pay_order")
public class PayOrder {
    private Long id;
    private String orderNo;
    private Long appointmentId;
    private Long patientId;
    private Long doctorId;
    private Long deptId;
    private BigDecimal totalAmount;
    private Integer status;
    private Date payTime;
    private Long payerId;
    private Date expireTime;
    private Date createTime;
    private Date updateTime;
}
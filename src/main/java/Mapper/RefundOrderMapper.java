package Mapper;

import PoJo.RefundOrder;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDate;

@Mapper
public interface RefundOrderMapper extends BaseMapper<RefundOrder> {

    /** 统计某日退款总金额 */
    BigDecimal sumRefundByDate(@Param("date") LocalDate date);

    /** 统计某日退款笔数 */
    Long countRefundByDate(@Param("date") LocalDate date);
}

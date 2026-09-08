package Mapper;

import PoJo.PayOrder;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Mapper
public interface PayOrderMapper extends BaseMapper<PayOrder> {

    /**
     * 日终结算：统计当日各支付方式营收
     */
    List<Map<String, Object>> dailyRevenueStats(@Param("date") LocalDate date);

    /**
     * 营收统计：按日期范围和条件查询
     */
    List<Map<String, Object>> revenueStats(@Param("startDate") String startDate,
                                           @Param("endDate") String endDate,
                                           @Param("deptId") Long deptId,
                                           @Param("doctorId") Long doctorId,
                                           @Param("payMethod") String payMethod);

    /**
     * 统计某日已缴费订单总数
     */
    Long countPaidByDate(@Param("date") LocalDate date);

    /**
     * 统计某日已缴费总金额
     */
    BigDecimal sumPaidByDate(@Param("date") LocalDate date);
}

package Mapper;

import PoJo.BillItem;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface BillItemMapper extends BaseMapper<BillItem> {

    /** 批量插入费用明细 */
    int insertBatch(@Param("list") List<BillItem> list);

    /** 根据订单号查询费用明细 */
    List<BillItem> selectByOrderNo(@Param("orderNo") String orderNo);
}

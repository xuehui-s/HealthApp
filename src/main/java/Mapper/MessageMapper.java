package Mapper;

import PoJo.SysMessage;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MessageMapper extends BaseMapper<SysMessage> {
    // 批量插入
    void insertBatch(@Param("list") List<SysMessage> list);
}
package it.guowei.healthapp.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import it.guowei.healthapp.domain.entity.OperationLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OperationLogMapper extends BaseMapper<OperationLog> {
}

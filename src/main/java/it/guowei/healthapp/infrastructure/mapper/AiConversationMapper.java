package it.guowei.healthapp.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import it.guowei.healthapp.domain.entity.AiConversation;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AiConversationMapper extends BaseMapper<AiConversation> {
}

package it.guowei.healthapp.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import it.guowei.healthapp.domain.entity.AdminUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AdminUserMapper extends BaseMapper<AdminUser> {

    @Select("SELECT * FROM admin WHERE username = #{username}")
    AdminUser selectByUsername(String username);
}

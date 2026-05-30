package Mapper;

import PoJo.Doctor;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface DoctorMapper extends BaseMapper<Doctor> {
    // 根据用户名查询医生
    @Select("select * from doctor where username = #{username}")
    Doctor selectByUsername(String username);
}

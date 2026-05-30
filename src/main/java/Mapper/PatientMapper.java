package Mapper;

import PoJo.Patient;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PatientMapper extends BaseMapper<Patient> {
    @Select("select * from patient where username = #{username}")
    Patient selectByUsername(String username);
}

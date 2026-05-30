package Service;

import Dto.Result;
import PoJo.Doctor;

import java.util.List;

public interface DoctorLoginService {
    Result login(Doctor doctor);

    Result sendCode(String username);
    
    List<Doctor> getByDeptId(Integer deptId);
}

package Service;

import PoJo.Department;
import java.util.List;

public interface DepartmentService {
    /**
     * 查询所有科室
     */
    List<Department> list();
}
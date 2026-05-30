package Service.Impl;

import Mapper.DepartmentMapper;
import PoJo.Department;
import Service.DepartmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DepartmentServiceImpl implements DepartmentService {

    private final DepartmentMapper departmentMapper;

    @Override
    public List<Department> list() {
        return departmentMapper.selectList(null);
    }
}
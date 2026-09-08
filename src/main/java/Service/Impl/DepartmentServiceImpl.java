package Service.Impl;

import Mapper.DepartmentMapper;
import PoJo.Department;
import Service.DepartmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DepartmentServiceImpl implements DepartmentService {

    private final DepartmentMapper departmentMapper;

    @Override
    public List<Department> list() {
        List<Department> departments = departmentMapper.selectList(null);
        log.info("查询科室列表，共{}条记录", departments.size());
        if (departments != null && !departments.isEmpty()) {
            log.info("科室数据: {}", departments);
        } else {
            log.warn("科室列表为空，请检查数据库是否已初始化！");
        }
        return departments;
    }
}
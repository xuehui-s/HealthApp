package Service.Impl;

import Dto.Result;
import Mapper.DoctorMapper;
import PoJo.Doctor;
import Service.DoctorLoginService;
import it.guowei.healthapp.common.util.JwtUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class DoctorLoginServiceImpl implements DoctorLoginService {

    @Autowired
    private DoctorMapper doctorMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private JwtUtil jwtUtil;

    // ====================== 登录 ======================
    @Override
    public Result login(Doctor doctor) {
        String username = doctor.getUsername();
        String password = doctor.getPassword();
        String userInputCode = doctor.getCode();

        log.info("===== 医生登录 =====");
        log.info("账号：{}", username);

        // 1. 校验验证码（先不删！）
        String redisKey = "DOCTOR_LOGIN_CODE_" + username;
        String realCode = redisTemplate.opsForValue().get(redisKey);

        if (realCode == null) {
            return Result.fail("验证码已过期");
        }
        if (!realCode.equals(userInputCode)) {
            return Result.fail("验证码错误");
        }

        // 2. 查询医生
        Doctor dbDoctor = doctorMapper.selectByUsername(username);
        if (dbDoctor == null) {
            return Result.fail("医生账号不存在");
        }

        // 3. 校验密码：用 BCrypt 匹配（重点改这里）
        if (!passwordEncoder.matches(password, dbDoctor.getPassword())) {
            log.warn("密码错误！用户输入: {}, 数据库密文: {}", password, dbDoctor.getPassword());
            return Result.fail("密码错误");
        }

        // ---------- 所有逻辑走完，最后再删验证码 ----------
        redisTemplate.delete(redisKey);

        // 4. 生成JWT并存入Redis（企业版Token携带 doctorId/userType 声明）
        String token = jwtUtil.generateToken(Long.valueOf(dbDoctor.getId()), dbDoctor.getUsername(), 2);

        // 将 Token 存入 Redis，设置30分钟滑动过期
        String jwtRedisKey = "JWT_TOKEN_" + dbDoctor.getUsername();
        redisTemplate.opsForValue().set(jwtRedisKey, token, 30, TimeUnit.MINUTES);
        log.info("医生登录成功，Token已存入Redis，用户: {}", dbDoctor.getUsername());

        // 返回 token 和用户信息（前端需要 doctorId 调用管理接口）
        java.util.Map<String, Object> data = new java.util.HashMap<>();
        data.put("token", token);
        data.put("doctorId", dbDoctor.getId());
        data.put("username", dbDoctor.getUsername());
        data.put("name", dbDoctor.getName());
        return Result.ok(data);
    }

    // ====================== 发送验证码（1分钟防刷） ======================
    @Override
    public Result sendCode(String username) {
        // 1. 防刷：1分钟只能发一次
        String lockKey = "DOCTOR_CODE_LOCK_" + username;
        Boolean lock = redisTemplate.hasKey(lockKey);
        if (Boolean.TRUE.equals(lock)) {
            return Result.fail("操作频繁，请1分钟后再试");
        }

        // 2. 生成6位验证码
        String code = String.valueOf((int) ((Math.random() * 9 + 1) * 100000));

        // 3. 存入Redis 5分钟
        redisTemplate.opsForValue().set("DOCTOR_LOGIN_CODE_" + username, code, 5, TimeUnit.MINUTES);

        // 4. 加锁60秒
        redisTemplate.opsForValue().set(lockKey, "locked", 60, TimeUnit.SECONDS);

        System.out.println("医生验证码：" + code);
        return Result.ok("发送成功");
    }

    // ====================== 根据科室查询医生 ======================
    @Override
    public List<Doctor> getByDeptId(Integer deptId) {
        return doctorMapper.selectList(Wrappers.lambdaQuery(Doctor.class)
                .eq(Doctor::getDepartmentId, deptId));
    }
}

package Service.Impl;
import Service.PatientLoginService;
import Dto.Result;
import Exception.BusinessException;
import Mapper.PatientMapper;
import PoJo.Patient;
import at.favre.lib.crypto.bcrypt.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import constant.BusinessCode;
import it.guowei.healthapp.common.util.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import java.util.Date;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class PatientLoginServiceImpl implements PatientLoginService {
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private PatientMapper patientMapper;
    @Autowired
    private JwtUtil jwtUtil;
    // 登录
    @Override
    public Result login(Patient patient) {
        String username = patient.getUsername();
        String password = patient.getPassword();
        String userInputCode = patient.getCode();
        log.info("用户名: {}", username);
        log.info("用户输入的验证码: {}", userInputCode);
        // ==========================================
        // 1. 先校验验证码（只验证，不删除）
        // ==========================================
        String redisKey = "LOGIN_CODE_" + username;
        log.info("Redis Key: {}", redisKey);

        String realCode = redisTemplate.opsForValue().get(redisKey);
        log.info("从 Redis 获取的验证码: {}", realCode);

        if (realCode == null) {
            log.warn("验证码不存在或已过期！Redis Key: {}", redisKey);
            return Result.fail("验证码已过期，请重新获取");
        }

        if (!realCode.equals(userInputCode)) {
            log.warn("验证码错误！Redis中的: {}, 用户输入的: {}", realCode, userInputCode);
            return Result.fail("验证码错误");
        }

        // ==========================================
        // 2. 再查数据库（账号是否存在）
        // ==========================================
        Patient dbPatient = patientMapper.selectByUsername(username);
        if (dbPatient == null) {
            return Result.fail("账号不存在");
        }

        // ==========================================
        // 3. 校验密码
        // ==========================================
        BCrypt.Result result = BCrypt.verifyer().verify(password.toCharArray(), dbPatient.getPassword());
        if (!result.verified) {
            return Result.fail("密码错误");
        }

        // ==========================================
        // ✅ 所有校验都成功了！最后再删除验证码！
        // ==========================================
        log.info("验证码校验成功，准备删除 Redis Key: {}", redisKey);
        redisTemplate.delete(redisKey);

        // ==========================================
        // 4. 登录成功，生成JWT并存入Redis
        //    （企业版Token携带 userId/userType 声明，网关据此填充用户上下文）
        // ==========================================
        String token = jwtUtil.generateToken(Long.valueOf(dbPatient.getId()), dbPatient.getUsername(), 1);

        // 将 Token 存入 Redis，设置30分钟滑动过期
        String jwtRedisKey = "JWT_TOKEN_" + username;
        redisTemplate.opsForValue().set(jwtRedisKey, token, 30, TimeUnit.MINUTES);
        log.info("登录成功，Token已存入Redis，用户: {}", username);

        // 返回 token 和用户信息（前端需要 patientId 调用缴费/消息接口）
        java.util.Map<String, Object> data = new java.util.HashMap<>();
        data.put("token", token);
        data.put("patientId", dbPatient.getId());
        data.put("username", dbPatient.getUsername());
        return Result.ok(data);
    }

//获取验证码
    @Override
    public Result getCode(String username) {

            // 1. 校验：1分钟内只能发一次！
            String lockKey = "CODE_LOCK_" + username;
            Boolean hasLock = redisTemplate.hasKey(lockKey);

            // 如果锁还存在 = 还在冷却中
            if (Boolean.TRUE.equals(hasLock)) {
                return Result.fail("操作过于频繁，请1分钟后再试");
            }

            // 2. 生成验证码
            String code = String.valueOf((int)((Math.random()*9+1)*100000)); // 6位

            // 3. 验证码存入Redis，5分钟有效
            redisTemplate.opsForValue().set("LOGIN_CODE_" + username, code, 5, TimeUnit.MINUTES);

            // 4. ✅ 加锁：1分钟内不能再次发送！
            redisTemplate.opsForValue().set(lockKey, "locked", 60, TimeUnit.SECONDS);

            // 5. 这里调用短信SDK（你自己的）
            System.out.println("手机号：" + username + "，验证码：" + code);

            return Result.ok("验证码发送成功");

    }
//患者注册功能
    @Override
    public void register(Patient patient) {


            // 1. 获取前端传的 手机号、密码
            String username = patient.getUsername();
            String password = patient.getPassword();

            // 2. 校验手机号是否已被注册
            LambdaQueryWrapper<Patient> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(Patient::getUsername, username);
            Patient existPatient = patientMapper.selectOne(queryWrapper);

            if (existPatient != null) {
                throw new BusinessException(BusinessCode.PHONE_REGISTERED);
            }

            // 3. 密码加密（BCrypt）
            String encodePassword = BCrypt.withDefaults().hashToString(12, password.toCharArray());

            // 4. 封装要存入数据库的患者信息
            Patient newPatient = new Patient();
            newPatient.setUsername(username);
            newPatient.setPassword(encodePassword);
            newPatient.setPhone(username); // 手机号同步存phone字段
            newPatient.setStatus(1);       // 正常状态
            newPatient.setCreateTime(new Date());
            newPatient.setUpdateTime(new Date());

            // 5. 插入数据库
            patientMapper.insert(newPatient);
        }
}
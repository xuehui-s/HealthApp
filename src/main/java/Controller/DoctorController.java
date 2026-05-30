package Controller;

import Dto.Result;
import PoJo.Doctor;
import Service.DoctorLoginService;
import Util.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/doctor")
public class DoctorController {
    @Autowired
    private DoctorLoginService doctorService;
    
    @Autowired
    private StringRedisTemplate redisTemplate;
    
    
    //医生登录功能
    @RequestMapping("/login")
    public Result login(@RequestBody Doctor doctor) {
        log.info("医生登录");
        return doctorService.login(doctor);
    }
    
    // 发送验证码
    @GetMapping("/sendCode")
    public Result sendCode(String username) {
        return doctorService.sendCode(username);
    }
    
    // 退出登录接口
    @PostMapping("/logout")
    public Result logout(@RequestHeader("Authorization") String token) {
        // 去除 "Bearer " 前缀
        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        
        // 解析 token 获取用户名
        String username = JwtUtil.extractUsername(token);
        if (username != null) {
            // 从 Redis 中删除 Token
            String redisKey = "JWT_TOKEN_" + username;
            redisTemplate.delete(redisKey);
            log.info("医生退出登录: {}", username);
        }
        
        return Result.ok("退出成功");
    }
}

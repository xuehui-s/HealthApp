package Controller;

import Dto.Result;
import PoJo.Patient;
import Service.PatientLoginService;
import Util.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/patient")
public class PatientController {

  // 注入 Service
  @Autowired
  private PatientLoginService patientService;
  
  @Autowired
  private StringRedisTemplate redisTemplate;

  @PostMapping("/login")
  public Result login(@RequestBody Patient patient) {
    log.info("用户登录：{}", patient.getUsername());

    // 只调用 Service，所有逻辑都在里面
    return patientService.login(patient);
  }
  // 获取验证码接口
  @GetMapping("/getCode")
  public Result getCode(String username) {
    return patientService.getCode(username);
  }

  // 注册接口
  @PostMapping("/register")
  public Result register(@RequestBody Patient patient) {
    patientService.register(patient);
    return Result.ok("注册成功");
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
      log.info("用户退出登录: {}", username);
    }
    
    return Result.ok("退出成功");
  }
}

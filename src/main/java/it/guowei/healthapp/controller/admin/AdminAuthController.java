package it.guowei.healthapp.controller.admin;

import it.guowei.healthapp.common.annotation.OperationLog;
import it.guowei.healthapp.common.context.UserContext;
import it.guowei.healthapp.common.exception.BusinessException;
import it.guowei.healthapp.common.result.Result;
import it.guowei.healthapp.common.result.ResultCode;
import it.guowei.healthapp.common.util.JwtUtil;
import it.guowei.healthapp.domain.entity.AdminUser;
import it.guowei.healthapp.infrastructure.mapper.AdminUserMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 管理端认证接口
 * 管理员账号存于 admin 表（BCrypt 密文），登录签发带 userType=3 的 JWT，
 * 并写入 Redis 会话（支持注销与单点互踢）。
 */
@Slf4j
@Tag(name = "管理端-认证", description = "管理员登录/登出/当前用户")
@RestController
@RequestMapping("/api/v1/admin/auth")
@RequiredArgsConstructor
public class AdminAuthController {

    private final AdminUserMapper adminUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final StringRedisTemplate redisTemplate;

    private static final String TOKEN_PREFIX = "JWT_TOKEN_";

    @Operation(summary = "管理员登录")
    @PostMapping("/login")
    @OperationLog(module = "管理端认证", description = "管理员登录", type = "LOGIN", recordParams = false)
    public Result<Map<String, Object>> login(@RequestBody LoginRequest req) {
        if (req.getUsername() == null || req.getUsername().isBlank()
                || req.getPassword() == null || req.getPassword().isBlank()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "账号和密码不能为空");
        }
        AdminUser admin = adminUserMapper.selectByUsername(req.getUsername().trim());
        if (admin == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        if (admin.getStatus() == null || admin.getStatus() != 1) {
            throw new BusinessException(ResultCode.USER_ACCOUNT_DISABLED);
        }
        if (!passwordEncoder.matches(req.getPassword(), admin.getPassword())) {
            throw new BusinessException(ResultCode.USER_PASSWORD_ERROR);
        }

        // userType=3 管理员；写入 Redis 会话（单点互踢：重新登录后旧Token失效）
        String token = jwtUtil.generateToken(admin.getId(), admin.getUsername(), 3);
        redisTemplate.opsForValue().set(TOKEN_PREFIX + admin.getUsername(), token, 30, TimeUnit.MINUTES);

        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("userId", admin.getId());
        data.put("username", admin.getUsername());
        data.put("name", admin.getName());
        data.put("role", admin.getRole());
        log.info("管理员登录成功: {}", admin.getUsername());
        return Result.ok(data);
    }

    @Operation(summary = "当前登录管理员")
    @GetMapping("/me")
    public Result<Map<String, Object>> me() {
        Map<String, Object> data = new HashMap<>();
        data.put("userId", UserContext.getUserId());
        data.put("username", UserContext.getUsername());
        data.put("userType", UserContext.getUserType());
        return Result.ok(data);
    }

    @Operation(summary = "退出登录")
    @PostMapping("/logout")
    public Result<Void> logout() {
        String username = UserContext.getUsername();
        if (username != null) {
            redisTemplate.delete(TOKEN_PREFIX + username);
        }
        return Result.ok();
    }

    @Data
    public static class LoginRequest {
        private String username;
        private String password;
    }
}

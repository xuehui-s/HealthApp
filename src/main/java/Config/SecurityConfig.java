package Config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // 禁用 CSRF（前后端分离 + JWT 场景）
            .csrf(csrf -> csrf.disable())
            // 无状态会话
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // 请求授权规则
            .authorizeHttpRequests(auth -> auth
                // Actuator 健康检查端点
                .requestMatchers("/actuator/**").permitAll()
                // Swagger/OpenAPI 文档
                .requestMatchers(
                    "/v3/api-docs/**",
                    "/swagger-ui/**",
                    "/swagger-ui.html",
                    "/swagger-resources/**"
                ).permitAll()
                // 静态资源
                .requestMatchers(
                    "/static/**",
                    "/index.html",
                    "/admin.html",
                    "/css/**",
                    "/js/**",
                    "/favicon.ico"
                ).permitAll()
                // 放行登录/注册相关接口（由 JwtInterceptor 处理）
                .requestMatchers(
                    "/patient/login",
                    "/patient/getCode",
                    "/patient/register",
                    "/doctor/login",
                    "/doctor/sendCode",
                    "/api/v1/admin/auth/login"
                ).permitAll()
                // 放行所有请求（由自定义 JwtInterceptor 处理 JWT 验证）
                .anyRequest().permitAll()
            )
            // 禁用默认的 formLogin 和 httpBasic
            .formLogin(form -> form.disable())
            .httpBasic(basic -> basic.disable());

        return http.build();
    }
}

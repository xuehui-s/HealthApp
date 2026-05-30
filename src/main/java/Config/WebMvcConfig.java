package Config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Autowired
    private JwtInterceptor jwtInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtInterceptor)
                // 拦截所有请求
                .addPathPatterns("/**")
                // 排除不需要登录的接口
                .excludePathPatterns(
                        "/patient/login",           // 患者登录
                        "/patient/getCode",          // 患者获取验证码
                        "/patient/register",         // 患者注册
                        "/doctor/login",             // 医生登录
                        "/doctor/sendCode"           // 医生获取验证码
                );
    }
}

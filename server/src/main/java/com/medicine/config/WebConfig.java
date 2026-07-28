package com.medicine.config;

import com.medicine.security.JwtInterceptor;
import com.medicine.security.RoleInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final JwtInterceptor jwtInterceptor;
    private final RoleInterceptor roleInterceptor;

    public WebConfig(JwtInterceptor jwtInterceptor, RoleInterceptor roleInterceptor) {
        this.jwtInterceptor = jwtInterceptor;
        this.roleInterceptor = roleInterceptor;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins("*")
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // JWT 认证拦截器 — 白名单外的路径必须登录
        registry.addInterceptor(jwtInterceptor)
                .addPathPatterns("/api/v1/**")
                .excludePathPatterns(
                        "/api/v1/auth/login",
                        "/api/v1/auth/register",
                        "/api/v1/auth/refresh",
                        "/api/v1/health",
                        "/api/v1/medicines",
                        "/api/v1/medicines/**",
                        "/api/v1/upload/**"
                );

        // 角色拦截器 — admin/pharmacist 路径额外校验角色
        registry.addInterceptor(roleInterceptor)
                .addPathPatterns(
                        "/api/v1/admin/**",
                        "/api/v1/pharmacist/**"
                );
    }
}

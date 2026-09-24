package com.xqfx.requirements.user;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 注册登录拦截器。
 *
 * <p>采用白名单方式：除登录接口和健康检查外，{@code /api/**} 全部需要登录。
 * 新增接口默认受保护，不会因为忘记配置而意外暴露。
 */
@Configuration
class AuthWebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;

    AuthWebConfig(AuthInterceptor authInterceptor) {
        this.authInterceptor = authInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/auth/**",
                        "/api/health");
    }
}

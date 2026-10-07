package com.fz.scitech.config;

import com.fz.scitech.platform.auth.AuthInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;

/**
 * Web 配置。
 * CORS 收紧(决策#35):来源白名单读 sc.cors-origin(逗号分隔,支持通配符),
 * dev 默认 http://localhost:5173,上线改校内实际域名。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final String[] corsOrigins;

    public WebConfig(AuthInterceptor authInterceptor,
                     @Value("${sc.cors-origin}") String corsOrigin) {
        this.authInterceptor = authInterceptor;
        this.corsOrigins = Arrays.stream(corsOrigin.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/auth/login", "/error");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns(corsOrigins)
                .allowedMethods("*")
                .allowedHeaders("*");
    }
}

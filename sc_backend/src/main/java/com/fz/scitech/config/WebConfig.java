package com.fz.scitech.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;

/**
 * Web 配置。
 * CORS 收紧(决策#35):来源白名单读 sc.cors-origin(逗号分隔,支持通配符),
 * dev 默认 http://localhost:5173,上线改校内实际域名。
 * 拦截器注册在 platform/auth 改造完成后由该包补充(Task 5)。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final String[] corsOrigins;

    public WebConfig(@Value("${sc.cors-origin}") String corsOrigin) {
        this.corsOrigins = Arrays.stream(corsOrigin.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns(corsOrigins)
                .allowedMethods("*")
                .allowedHeaders("*");
    }
}

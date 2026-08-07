package com.qst.smartsite.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;
import java.nio.file.Paths;

/**
 * Web MVC 配置：拦截器注册 + 跨域支持 + SPA 路由 fallback
 * SPA fallback：Vue history 模式路由（/login、/dashboard 等）直接访问或刷新时，
 * 后端没有对应静态文件，转发到 index.html 由 Vue Router 接管渲染
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Autowired
    private JwtInterceptor jwtInterceptor;

    /** AI 告警截图目录（独立于 classpath，IDEA/mvn 启动行为一致，mvn clean 不丢失） */
    @Value("${ai.capture-dir}")
    private String captureDir;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/auth/login");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }

    /**
     * 静态资源 + SPA fallback：
     * - 存在的静态文件（index.html、assets/**、ai-capture/**）正常返回
     * - 前端路由（无对应文件且非 /api、非 WebSocket）返回 index.html
     * - /api/** 与 /ws 不 fallback，保持原有 404/JSON/握手行为
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // AI 告警截图：文件系统目录映射（先注册，具体 pattern 优先于 /**）
        String captureLocation = "file:" + Paths.get(captureDir).toAbsolutePath().normalize() + "/";
        registry.addResourceHandler("/ai-capture/**")
                .addResourceLocations(captureLocation);
        // 静态资源 + SPA fallback
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        Resource requested = location.createRelative(resourcePath);
                        if (requested.exists() && requested.isReadable()) {
                            return requested;
                        }
                        if (resourcePath.startsWith("api/") || resourcePath.startsWith("ws")) {
                            return null;
                        }
                        return new ClassPathResource("/static/index.html");
                    }
                });
    }
}

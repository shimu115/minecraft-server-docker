package com.mcpanel.panel.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

/**
 * SPA fallback 配置。
 * 非 /api/* 的 GET 请求全部 fallback 到 index.html。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        Resource requested = location.createRelative(resourcePath);

                        // 如果请求的资源存在且可读，直接返回
                        if (requested.exists() && requested.isReadable()) {
                            return requested;
                        }

                        // 否则 fallback 到 index.html（SPA 路由）
                        Resource fallback = new ClassPathResource("/static/index.html");
                        if (fallback.exists()) {
                            return fallback;
                        }

                        return null;
                    }
                });
    }
}

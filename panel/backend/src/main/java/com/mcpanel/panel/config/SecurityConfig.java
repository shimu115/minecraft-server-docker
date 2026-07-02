package com.mcpanel.panel.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security 配置。
 * JWT 无状态认证，CSRF 禁用。
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    private JwtAuthFilter jwtAuthFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // 登录接口放行
                        .requestMatchers("/api/auth/login").permitAll()
                        // 静态资源 + SPA 入口放行
                        .requestMatchers("/", "/index.html", "/favicon.ico").permitAll()
                        .requestMatchers("/assets/**", "/js/**", "/css/**").permitAll()
                        // H2 控制台放行（开发环境）
                        .requestMatchers("/h2-console/**").permitAll()
                        // Admin 接口需 ROOT 或 ADMIN 角色（具体权限由 Service 层 + AOP 控制）
                        .requestMatchers("/api/admin/**").hasAnyRole("ROOT", "ADMIN")
                        // Server 代理接口需认证即可（实例级访问由 @RequireInstanceAccess 切面控制）
                        .requestMatchers("/api/server/**").authenticated()
                        // 其他全部放行（SPA fallback 等）
                        .anyRequest().permitAll()
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                // H2 控制台需要禁用 frameOptions
                .headers(headers -> headers.frameOptions(fo -> fo.sameOrigin()));

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}

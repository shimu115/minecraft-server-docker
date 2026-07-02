package com.mcpanel.panel.controller;

import com.mcpanel.panel.common.ApiResponse;
import com.mcpanel.panel.common.ErrorCode;
import com.mcpanel.panel.config.JwtAuthFilter;
import com.mcpanel.panel.exception.McPanelException;
import com.mcpanel.panel.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    /**
     * 用户名密码登录，返回 JWT。
     */
    @PostMapping("/auth/login")
    public ApiResponse<Map<String, String>> login(@Valid @RequestBody LoginRequest request) {
        String token = userService.authenticate(request.username, request.password);
        return ApiResponse.success(Map.of("token", token));
    }

    /**
     * 获取当前用户信息。
     */
    @GetMapping("/auth/get-me")
    public ApiResponse<?> getMe() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof JwtAuthFilter.JwtUserPrincipal user)) {
            throw new McPanelException(ErrorCode.UNAUTHORIZED);
        }
        return ApiResponse.success(Map.of(
                "userId", user.userId(),
                "username", user.username(),
                "role", user.role()
        ));
    }

    public static class LoginRequest {
        @NotBlank
        public String username;
        @NotBlank
        public String password;
    }
}

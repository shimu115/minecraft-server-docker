package com.mcpanel.panel.controller;

import com.mcpanel.panel.common.ApiResponse;
import com.mcpanel.panel.common.ErrorCode;
import com.mcpanel.panel.config.JwtAuthFilter;
import com.mcpanel.panel.dto.LoginRequest;
import com.mcpanel.panel.dto.LoginResponse;
import com.mcpanel.panel.dto.UserInfoResponse;
import com.mcpanel.panel.exception.McPanelException;
import com.mcpanel.panel.service.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class AuthController {

    @Autowired
    private UserService userService;

    @PostMapping("/auth/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        String token = userService.authenticate(request.getUsername(), request.getPassword());
        return ApiResponse.success(new LoginResponse(token));
    }

    @GetMapping("/auth/get-me")
    public ApiResponse<UserInfoResponse> getMe() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof JwtAuthFilter.JwtUserPrincipal user)) {
            throw new McPanelException(ErrorCode.UNAUTHORIZED);
        }
        return ApiResponse.success(new UserInfoResponse(user.userId(), user.username(), user.role()));
    }
}

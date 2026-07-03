package com.mcpanel.panel.service;

import com.mcpanel.panel.dto.UserResponse;

import java.util.List;

public interface UserService {

    String authenticate(String username, String password);

    UserResponse createUser(String username, String password, String role);

    List<UserResponse> listUsers();

    void bindInstance(Long userId, Long instanceId);

    void unbindInstance(Long userId, Long instanceId);
}

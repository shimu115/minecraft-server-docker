package com.mcpanel.panel.service.impl;

import com.mcpanel.panel.common.ErrorCode;
import com.mcpanel.panel.config.JwtUtil;
import com.mcpanel.panel.dto.UserResponse;
import com.mcpanel.panel.entity.User;
import com.mcpanel.panel.entity.UserInstance;
import com.mcpanel.panel.exception.McPanelException;
import com.mcpanel.panel.repository.ServerInstanceRepository;
import com.mcpanel.panel.repository.UserInstanceRepository;
import com.mcpanel.panel.repository.UserRepository;
import com.mcpanel.panel.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private UserInstanceRepository userInstanceRepository;
    @Autowired
    private ServerInstanceRepository serverInstanceRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JwtUtil jwtUtil;

    @Override
    public String authenticate(String username, String password) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new McPanelException(ErrorCode.INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new McPanelException(ErrorCode.INVALID_CREDENTIALS);
        }

        return jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());
    }

    @Override
    @Transactional
    public UserResponse createUser(String username, String password, String role) {
        if (userRepository.existsByUsername(username)) {
            throw new McPanelException(ErrorCode.USERNAME_EXISTS);
        }

        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRole(role != null ? role : "USER");
        user = userRepository.save(user);

        return new UserResponse(user.getId(), user.getUsername(), user.getRole(), user.getCreatedAt());
    }

    @Override
    public List<UserResponse> listUsers() {
        return userRepository.findAll().stream()
                .map(u -> new UserResponse(u.getId(), u.getUsername(), u.getRole(), u.getCreatedAt()))
                .toList();
    }

    @Override
    @Transactional
    public void bindInstance(Long userId, Long instanceId) {
        if (!userRepository.existsById(userId)) {
            throw new McPanelException(ErrorCode.USER_NOT_FOUND);
        }

        if (!serverInstanceRepository.existsById(instanceId)) {
            throw new McPanelException(ErrorCode.INSTANCE_NOT_FOUND);
        }

        if (userInstanceRepository.existsByUserIdAndInstanceId(userId, instanceId)) {
            return;
        }

        UserInstance binding = new UserInstance();
        binding.setUserId(userId);
        binding.setInstanceId(instanceId);
        userInstanceRepository.save(binding);
    }

    @Override
    @Transactional
    public void unbindInstance(Long userId, Long instanceId) {
        if (!userRepository.existsById(userId)) {
            throw new McPanelException(ErrorCode.USER_NOT_FOUND);
        }
        userInstanceRepository.deleteByUserIdAndInstanceId(userId, instanceId);
    }
}

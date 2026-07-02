package com.mcpanel.panel.service;

import com.mcpanel.panel.common.ErrorCode;
import com.mcpanel.panel.config.JwtUtil;
import com.mcpanel.panel.entity.User;
import com.mcpanel.panel.entity.UserInstance;
import com.mcpanel.panel.exception.McPanelException;
import com.mcpanel.panel.repository.ServerInstanceRepository;
import com.mcpanel.panel.repository.UserInstanceRepository;
import com.mcpanel.panel.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final UserInstanceRepository userInstanceRepository;
    private final ServerInstanceRepository serverInstanceRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public UserService(UserRepository userRepository,
                       UserInstanceRepository userInstanceRepository,
                       ServerInstanceRepository serverInstanceRepository,
                       PasswordEncoder passwordEncoder,
                       JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.userInstanceRepository = userInstanceRepository;
        this.serverInstanceRepository = serverInstanceRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    /**
     * 用户名密码登录，返回 JWT。
     */
    public String authenticate(String username, String password) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new McPanelException(ErrorCode.INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new McPanelException(ErrorCode.INVALID_CREDENTIALS);
        }

        return jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());
    }

    /**
     * 创建用户（仅 Root 可调用）。
     */
    @Transactional
    public User createUser(String username, String password, String role) {
        if (userRepository.existsByUsername(username)) {
            throw new McPanelException(ErrorCode.USERNAME_EXISTS);
        }

        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRole(role != null ? role : "USER");

        return userRepository.save(user);
    }

    /**
     * 列出所有用户。
     */
    public List<User> listUsers() {
        return userRepository.findAll();
    }

    /**
     * 为用户绑定实例。
     */
    @Transactional
    public void bindInstance(Long userId, Long instanceId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new McPanelException(ErrorCode.USER_NOT_FOUND));

        if (!serverInstanceRepository.existsById(instanceId)) {
            throw new McPanelException(ErrorCode.INSTANCE_NOT_FOUND);
        }

        if (userInstanceRepository.existsByUserIdAndInstanceId(userId, instanceId)) {
            return; // 已绑定，幂等
        }

        UserInstance binding = new UserInstance();
        binding.setUserId(userId);
        binding.setInstanceId(instanceId);
        userInstanceRepository.save(binding);
    }

    /**
     * 解除用户的实例绑定。
     */
    @Transactional
    public void unbindInstance(Long userId, Long instanceId) {
        if (!userRepository.existsById(userId)) {
            throw new McPanelException(ErrorCode.USER_NOT_FOUND);
        }

        userInstanceRepository.deleteByUserIdAndInstanceId(userId, instanceId);
    }
}

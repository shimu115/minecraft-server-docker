package com.mcpanel.panel.config;

import com.mcpanel.panel.entity.User;
import com.mcpanel.panel.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 数据初始化器。
 * 首次启动时自动创建默认 Root 用户。
 */
@Component
public class DataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @Value("${app.root-user.username}")
    private String rootUsername;

    @Value("${app.root-user.password}")
    private String rootPassword;

    @Override
    public void run(ApplicationArguments args) {
        if (!userRepository.existsByUsername(rootUsername)) {
            User root = new User();
            root.setUsername(rootUsername);
            root.setPasswordHash(passwordEncoder.encode(rootPassword));
            root.setRole("ROOT");
            userRepository.save(root);
            log.info("[mc-panel] 默认 Root 用户已创建: {}", rootUsername);
        }
    }
}

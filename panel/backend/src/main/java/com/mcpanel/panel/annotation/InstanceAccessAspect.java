package com.mcpanel.panel.annotation;

import com.mcpanel.panel.common.ErrorCode;
import com.mcpanel.panel.config.JwtAuthFilter;
import com.mcpanel.panel.exception.McPanelException;
import com.mcpanel.panel.repository.UserInstanceRepository;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * RequireInstanceAccess 切面实现。
 * 拦截标记了 @RequireInstanceAccess 的方法，校验实例级访问权限。
 */
@Aspect
@Component
public class InstanceAccessAspect {

    private static final Logger log = LoggerFactory.getLogger(InstanceAccessAspect.class);

    private final UserInstanceRepository userInstanceRepository;

    public InstanceAccessAspect(UserInstanceRepository userInstanceRepository) {
        this.userInstanceRepository = userInstanceRepository;
    }

    @Before("@annotation(com.mcpanel.panel.annotation.RequireInstanceAccess) && args(instanceId,..)")
    public void checkAccess(Long instanceId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !auth.isAuthenticated()) {
            throw new McPanelException(ErrorCode.UNAUTHORIZED);
        }

        Object principal = auth.getPrincipal();
        if (!(principal instanceof JwtAuthFilter.JwtUserPrincipal user)) {
            throw new McPanelException(ErrorCode.UNAUTHORIZED);
        }

        // ROOT 用户跳过绑定检查
        if (user.isRoot()) {
            return;
        }

        // 检查 user_instances 绑定
        boolean hasAccess = userInstanceRepository
                .existsByUserIdAndInstanceId(user.userId(), instanceId);

        if (!hasAccess) {
            log.warn("[mc-panel] 用户 {} (id={}) 尝试访问未绑定的实例 id={}",
                    user.username(), user.userId(), instanceId);
            throw new McPanelException(ErrorCode.INSTANCE_NOT_BOUND);
        }
    }
}

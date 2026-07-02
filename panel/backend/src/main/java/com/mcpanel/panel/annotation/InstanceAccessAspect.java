package com.mcpanel.panel.annotation;

import com.mcpanel.panel.common.ErrorCode;
import com.mcpanel.panel.config.JwtAuthFilter;
import com.mcpanel.panel.exception.McPanelException;
import com.mcpanel.panel.repository.UserInstanceRepository;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class InstanceAccessAspect {

    private static final Logger log = LoggerFactory.getLogger(InstanceAccessAspect.class);

    @Autowired
    private UserInstanceRepository userInstanceRepository;

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

        if (user.isRoot()) {
            return;
        }

        boolean hasAccess = userInstanceRepository
                .existsByUserIdAndInstanceId(user.userId(), instanceId);

        if (!hasAccess) {
            log.warn("[mc-panel] 用户 {} (id={}) 尝试访问未绑定的实例 id={}",
                    user.username(), user.userId(), instanceId);
            throw new McPanelException(ErrorCode.INSTANCE_NOT_BOUND);
        }
    }
}

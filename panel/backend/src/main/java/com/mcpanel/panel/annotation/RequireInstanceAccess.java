package com.mcpanel.panel.annotation;

import java.lang.annotation.*;

/**
 * 实例级访问控制注解。
 * 标记在 Controller 方法上，AOP 切面自动校验当前用户是否有权访问该实例。
 *
 * ROOT  → 放行（不查 user_instances）
 * ADMIN → 查 user_instances，有绑定则放行
 * USER  → 查 user_instances，有绑定则放行
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireInstanceAccess {
}

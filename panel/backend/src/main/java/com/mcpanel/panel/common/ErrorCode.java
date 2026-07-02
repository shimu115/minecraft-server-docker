package com.mcpanel.panel.common;

import lombok.Getter;

/**
 * 错误码枚举。
 * code 与 msg 强绑定，分段管理：
 * 1xxx 通用、2xxx 认证、3xxx Key、4xxx 实例、5xxx 用户、6xxx Agent
 */
@Getter
public enum ErrorCode {

    // ==================== 成功 ====================
    SUCCESS(200, "ok"),

    // ==================== 通用错误 1xxx ====================
    BAD_REQUEST(1000, "请求参数错误"),
    INTERNAL_ERROR(1001, "服务器内部错误"),

    // ==================== 认证错误 2xxx ====================
    UNAUTHORIZED(2000, "未登录或登录已过期"),
    FORBIDDEN(2001, "无权限执行此操作"),
    INVALID_CREDENTIALS(2002, "用户名或密码错误"),

    // ==================== API Key 错误 3xxx ====================
    KEY_NOT_FOUND(3000, "API Key 不存在"),
    KEY_ALREADY_EXISTS(3001, "该 API Key 已注册"),
    KEY_ALREADY_BOUND(3002, "该 API Key 已被其他实例绑定"),
    KEY_INVALID_FORMAT(3003, "API Key 格式无效，需 UUID v4 格式"),
    KEY_REVOKED(3004, "该 API Key 已吊销"),
    KEY_BOUND_CANNOT_DELETE(3005, "该 Key 已绑定实例，请先解绑再删除"),

    // ==================== 实例错误 4xxx ====================
    INSTANCE_NOT_FOUND(4000, "MC 实例不存在"),
    INSTANCE_NAME_EXISTS(4001, "实例名称已存在"),
    INSTANCE_NOT_BOUND(4002, "当前用户未绑定该实例，无法操作"),

    // ==================== 用户错误 5xxx ====================
    USER_NOT_FOUND(5000, "用户不存在"),
    USERNAME_EXISTS(5001, "用户名已存在"),
    USER_NOT_BOUND(5002, "用户未绑定任何实例"),

    // ==================== Agent 通信错误 6xxx ====================
    AGENT_UNREACHABLE(6000, "无法连接到 Go API"),
    AGENT_TIMEOUT(6001, "Go API 请求超时"),
    AGENT_ERROR(6002, "Go API 返回错误"),
    AGENT_REFRESH_FAILED(6003, "刷新 Go API Key 失败"),
    ;

    private final int code;
    private final String msg;

    ErrorCode(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }

}

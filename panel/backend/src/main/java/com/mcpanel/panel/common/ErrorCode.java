package com.mcpanel.panel.common;

import lombok.Getter;

/**
 * 错误码枚举。
 * code 与 msg 强绑定，按业务模块分段管理：
 * <pre>
 * 200             成功
 * 10000 - 19999   通用错误
 * 20000 - 29999   认证授权
 * 30000 - 39999   用户
 * 40000 - 49999   API Key
 * 50000 - 59999   实例
 * 60000 - 69999   Agent 通信
 * </pre>
 *
 * @see docs/specification/error-code-specification.md
 */
@Getter
public enum ErrorCode {

    // ==================== 成功 ====================
    SUCCESS(200, "ok"),

    // ==================== 通用错误 10000 - 19999 ====================
    BAD_REQUEST(10000, "请求参数错误"),
    INTERNAL_ERROR(10001, "服务器内部错误"),

    // ==================== 认证授权 20000 - 29999 ====================
    UNAUTHORIZED(20000, "未登录或登录已过期"),
    FORBIDDEN(20001, "无权限执行此操作"),
    INVALID_CREDENTIALS(20002, "用户名或密码错误"),

    // ==================== 用户 30000 - 39999 ====================
    USER_NOT_FOUND(30000, "用户不存在"),
    USERNAME_EXISTS(30001, "用户名已存在"),
    USER_NOT_BOUND(30002, "用户未绑定任何实例"),

    // ==================== API Key 40000 - 49999 ====================
    KEY_NOT_FOUND(40000, "API Key 不存在"),
    KEY_ALREADY_EXISTS(40001, "该 API Key 已注册"),
    KEY_ALREADY_BOUND(40002, "该 API Key 已被其他实例绑定"),
    KEY_INVALID_FORMAT(40003, "API Key 格式无效，需 UUID v4 格式"),
    KEY_REVOKED(40004, "该 API Key 已吊销"),
    KEY_BOUND_CANNOT_DELETE(40005, "该 Key 已绑定实例，请先解绑再删除"),

    // ==================== 实例 50000 - 59999 ====================
    INSTANCE_NOT_FOUND(50000, "MC 实例不存在"),
    INSTANCE_NAME_EXISTS(50001, "实例名称已存在"),
    INSTANCE_NOT_BOUND(50002, "当前用户未绑定该实例，无法操作"),

    // ==================== Agent 通信 60000 - 69999 ====================
    AGENT_UNREACHABLE(60000, "无法连接到 Go API"),
    AGENT_TIMEOUT(60001, "Go API 请求超时"),
    AGENT_ERROR(60002, "Go API 返回错误"),
    AGENT_REFRESH_FAILED(60003, "刷新 Go API Key 失败"),
    ;

    private final int code;
    private final String msg;

    ErrorCode(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }

}

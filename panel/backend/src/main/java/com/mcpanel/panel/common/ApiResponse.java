package com.mcpanel.panel.common;

/**
 * 统一响应包装类。
 * 所有接口使用此格式返回：{code, msg, data}。
 * HTTP 层始终返回 200，业务成功/失败由 code 区分。
 */
public class ApiResponse<T> {

    private int code;
    private String msg;
    private T data;

    private ApiResponse(int code, String msg, T data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
    }

    // === 成功 ===

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(200, "ok", data);
    }

    public static <T> ApiResponse<T> success() {
        return new ApiResponse<>(200, "ok", null);
    }

    // === 错误 ===

    public static <T> ApiResponse<T> error(ErrorCode ec) {
        return new ApiResponse<>(ec.getCode(), ec.getMsg(), null);
    }

    public static <T> ApiResponse<T> error(ErrorCode ec, T data) {
        return new ApiResponse<>(ec.getCode(), ec.getMsg(), data);
    }

    public static <T> ApiResponse<T> error(int code, String msg, T data) {
        return new ApiResponse<>(code, msg, data);
    }

    // === getters ===

    public int getCode() { return code; }
    public String getMsg() { return msg; }
    public T getData() { return data; }
}

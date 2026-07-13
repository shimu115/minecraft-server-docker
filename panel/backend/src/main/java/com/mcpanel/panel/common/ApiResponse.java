package com.mcpanel.panel.common;

import lombok.Builder;
import lombok.Getter;

/**
 * 统一响应包装类。
 * 所有接口使用此格式返回：{code, msg, data}。
 * HTTP 层始终返回 200，业务成功/失败由 code 区分。
 */
@Builder
@Getter
public class ApiResponse<T> {

    private int code;
    private String msg;
    private T data;

    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder().code(200).msg(ErrorCode.SUCCESS.getMsg()).data(data).build();
    }

    public static <T> ApiResponse<T> success() {
        return ApiResponse.<T>builder().code(200).msg(ErrorCode.SUCCESS.getMsg()).data(null).build();
    }

    public static <T> ApiResponse<T> error(ErrorCode ec) {
        return ApiResponse.<T>builder().code(ec.getCode()).msg(ec.getMsg()).data(null).build();
    }

    public static <T> ApiResponse<T> error(int code, String msg) {
        return ApiResponse.<T>builder().code(code).msg(msg).data(null).build();
    }

    public static <T> ApiResponse<T> error() {
        return ApiResponse.<T>builder()
                .code(ErrorCode.INTERNAL_ERROR.getCode())
                .msg(ErrorCode.INTERNAL_ERROR.getMsg())
                .data(null)
                .build();
    }

}

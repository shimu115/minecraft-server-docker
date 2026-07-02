package com.mcpanel.panel.exception;

import com.mcpanel.panel.common.ErrorCode;

/**
 * 自定义业务异常，携带 ErrorCode。
 * 在 Service/Controller 中遇到业务错误时直接抛出，
 * 由 TryCatchGlobalException 统一处理为 ApiResponse。
 */
public class McPanelException extends RuntimeException {

    private final ErrorCode errorCode;

    public McPanelException(ErrorCode errorCode) {
        super(errorCode.getMsg());
        this.errorCode = errorCode;
    }

    public McPanelException(ErrorCode errorCode, String detail) {
        super(detail);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}

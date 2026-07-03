package com.mcpanel.panel.config;

import com.alibaba.fastjson.JSON;
import com.mcpanel.panel.common.ApiResponse;
import com.mcpanel.panel.common.ErrorCode;
import com.mcpanel.panel.exception.McPanelException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.nio.charset.StandardCharsets;

/**
 * 全局异常捕捉器。
 * 拦截所有未处理异常，统一包装为 ApiResponse 格式返回。
 */
@RestControllerAdvice
@Slf4j
public class TryCatchGlobalException {


    /**
     * 自定义业务异常 —— 使用 ErrorCode 中绑定的 code + msg。
     * 若异常携带了不同于 ErrorCode 默认值的自定义消息，则优先使用自定义消息。
     */
    @ExceptionHandler(McPanelException.class)
    public ResponseEntity<ApiResponse<Void>> handleMcPanelException(
            McPanelException ex, HttpServletRequest request) {

        log.warn("[mc-panel] 业务异常 | {} {} | code={} msg={}",
                request.getMethod(), request.getRequestURI(),
                ex.getErrorCode().getCode(), ex.getMessage());

        ErrorCode ec = ex.getErrorCode();
        String msg = ex.getMessage();

        // 自定义消息优先（如 AgentClient 翻译的 Go API 错误）
        if (msg != null && !msg.equals(ec.getMsg())) {
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(ApiResponse.error(ec.getCode(), msg, null));
        }

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.error(ec));
    }

    /**
     * 参数校验异常（@Valid 失败）
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {

        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("参数校验失败");

        log.warn("[mc-panel] 参数校验失败 | {} {} | {}",
                request.getMethod(), request.getRequestURI(), detail);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), detail, null));
    }

    /**
     * 兜底 —— 未预期的运行时异常
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(
            Exception ex, HttpServletRequest request) {

        log.error("[mc-panel] 未捕获异常 | {} {} | {}",
                request.getMethod(), request.getRequestURI(), ex.getMessage(), ex);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.error(ErrorCode.INTERNAL_ERROR));
    }
}

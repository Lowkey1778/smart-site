package com.qst.smartsite.common;

/**
 * 业务异常：业务逻辑校验失败时抛出，由 GlobalExceptionHandler 统一处理
 */
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(String message) {
        super(message);
        this.code = 500;
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}

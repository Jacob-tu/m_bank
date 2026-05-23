package com.mbank.exception;

/**
 * 业务异常
 * 封装可预期的业务错误，携带 HTTP 状态码和错误消息
 */
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}

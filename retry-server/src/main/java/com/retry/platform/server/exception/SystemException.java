package com.retry.platform.server.exception;

/**
 * 系统异常
 * 用于表示系统级错误（如Redis连接失败、数据库异常），需要告警
 */
public class SystemException extends RuntimeException {
    
    private String errorCode;
    
    public SystemException(String message) {
        super(message);
    }
    
    public SystemException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public SystemException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
    
    public SystemException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
}

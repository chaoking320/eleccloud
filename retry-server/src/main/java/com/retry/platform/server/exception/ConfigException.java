package com.retry.platform.server.exception;

/**
 * 配置异常
 * 用于表示配置错误（如场景未配置、钩子类不存在），任务应标记为失败
 */
public class ConfigException extends RuntimeException {
    
    private String errorCode;
    
    public ConfigException(String message) {
        super(message);
    }
    
    public ConfigException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public ConfigException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
    
    public ConfigException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
}

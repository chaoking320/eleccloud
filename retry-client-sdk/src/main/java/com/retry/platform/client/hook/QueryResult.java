package com.retry.platform.client.hook;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 查询结果类
 * doQuery()方法的返回结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueryResult {
    
    /**
     * 查询是否成功
     */
    private boolean success;
    
    /**
     * 查询结果数据
     */
    private Object data;
    
    /**
     * 错误信息
     */
    private String errorMessage;
    
    /**
     * 创建成功结果
     */
    public static QueryResult success(Object data) {
        return QueryResult.builder()
                .success(true)
                .data(data)
                .build();
    }
    
    /**
     * 创建失败结果
     */
    public static QueryResult failure(String errorMessage) {
        return QueryResult.builder()
                .success(false)
                .errorMessage(errorMessage)
                .build();
    }
}

package com.retry.platform.client.api.impl;

import com.retry.platform.client.api.RetryClient;
import com.retry.platform.client.config.RetryClientProperties;
import com.retry.platform.client.dto.Result;
import com.retry.platform.client.dto.RetryTaskDTO;
import com.retry.platform.client.dto.RetryTaskRequest;
import com.retry.platform.client.util.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 重试客户端实现类
 */
@Slf4j
@Component
public class RetryClientImpl implements RetryClient {
    
    @Autowired
    private RetryClientProperties properties;
    
    @Autowired
    private RestTemplate restTemplate;
    
    private static final String SUBMIT_PATH = "/api/retry/submit";
    private static final String CANCEL_PATH = "/api/retry/cancel/";
    private static final String QUERY_PATH = "/api/retry/task/";
    private static final String SUCCESS_PATH = "/api/retry/success/";

    @Override
    public boolean markSuccess(String taskId) {
        if (properties.isDevMode()) {
            log.info("[DEV MODE] Mark task success: {}", taskId);
            return true;
        }
        if (taskId == null || taskId.isEmpty()) {
            throw new IllegalArgumentException("TaskId cannot be null or empty");
        }
        try {
            String url = properties.getServerUrl() + SUCCESS_PATH + taskId;
            ResponseEntity<Result<Boolean>> response = restTemplate.exchange(
                     url,
                     HttpMethod.POST,
                     null,
                     new ParameterizedTypeReference<Result<Boolean>>() {}
            );
            Result<Boolean> result = response.getBody();
            if (result != null && result.getSuccess()) {
                log.info("Marked task as SUCCESS: taskId={}", taskId);
                return true;
            } else {
                String errorMsg = result != null ? result.getMessage() : "Unknown error";
                log.error("Failed to mark task as success: taskId={}, error={}", taskId, errorMsg);
                return false;
            }
        } catch (Exception e) {
            log.error("Failed to mark task success: taskId={}", taskId, e);
            return false;
        }
    }

    @Override
    public boolean markExecuting(String taskId) {
        try {
            String url = properties.getServerUrl() + "/api/retry/executing/" + taskId;
            ResponseEntity<Result<Boolean>> response = restTemplate.exchange(
                    url, HttpMethod.POST, null, new ParameterizedTypeReference<Result<Boolean>>() {}
            );
            Result<Boolean> result = response.getBody();
            return result != null && result.getSuccess() && Boolean.TRUE.equals(result.getData());
        } catch (Exception e) {
            log.error("Failed to mark task executing: taskId={}", taskId, e);
            return false;
        }
    }

    @Override
    public void updateStatus(String taskId, String status) {
        try {
            String url = properties.getServerUrl() + "/api/retry/status?taskId=" + taskId + "&status=" + status;
            restTemplate.postForObject(url, null, Result.class);
        } catch (Exception e) {
            log.error("Failed to update status remotely: taskId={}", taskId, e);
        }
    }

    @Override
    public void updateRetryCountAndStatus(String taskId, int retryCount, String status) {
        try {
            String url = properties.getServerUrl() + "/api/retry/retry-info?taskId=" + taskId + "&retryCount=" + retryCount + "&status=" + status;
            restTemplate.postForObject(url, null, Result.class);
        } catch (Exception e) {
            log.error("Failed to update retry info remotely: taskId={}", taskId, e);
        }
    }

    @Override
    public void rollbackToPending(String taskId, String errorMsg) {
        try {
            // 修复 Bug5: 使用 UriComponentsBuilder 自动编码，防止 errorMsg 含 &/=/# 等特殊字符导致解析失败
            String url = UriComponentsBuilder
                    .fromHttpUrl(properties.getServerUrl() + "/api/retry/rollback")
                    .queryParam("taskId", taskId)
                    .queryParam("errorMsg", errorMsg)
                    .toUriString();
            restTemplate.postForObject(url, null, Result.class);
        } catch (Exception e) {
            log.error("Failed to rollback task remotely: taskId={}", taskId, e);
        }
    }

    @Override
    public void markFailed(String taskId, String reason) {
        try {
            // 修复 Bug5: 使用 UriComponentsBuilder 自动编码，防止 reason 含特殊字符导致解析失败
            String url = UriComponentsBuilder
                    .fromHttpUrl(properties.getServerUrl() + "/api/retry/failed")
                    .queryParam("taskId", taskId)
                    .queryParam("reason", reason)
                    .toUriString();
            restTemplate.postForObject(url, null, Result.class);
        } catch (Exception e) {
            log.error("Failed to mark task failed remotely: taskId={}", taskId, e);
        }
    }

    @Override
    public String submit(RetryTaskRequest request) {
        // 开发模式下仅记录日志
        if (properties.isDevMode()) {
            log.info("[DEV MODE] Retry task would be submitted: {}", JsonUtil.toJson(request));
            return "dev-mode-task-id";
        }
        
        // 参数校验
        validateRequest(request);
        
        try {
            String url = properties.getServerUrl() + SUBMIT_PATH;
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<RetryTaskRequest> entity = new HttpEntity<>(request, headers);
            
            ResponseEntity<Result<String>> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    new ParameterizedTypeReference<Result<String>>() {}
            );
            
            Result<String> result = response.getBody();
            if (result != null && result.getSuccess()) {
                return result.getData();
            } else {
                String errorMsg = result != null ? result.getMessage() : "Unknown error";
                throw new RuntimeException("Failed to submit retry task: " + errorMsg);
            }
        } catch (Exception e) {
            log.error("Failed to submit retry task to server", e);
            throw new RuntimeException("Failed to submit retry task", e);
        }
    }
    
    @Override
    public boolean cancel(String taskId) {
        // 开发模式下仅记录日志
        if (properties.isDevMode()) {
            log.info("[DEV MODE] Retry task would be cancelled: {}", taskId);
            return true;
        }
        
        if (taskId == null || taskId.isEmpty()) {
            throw new IllegalArgumentException("TaskId cannot be null or empty");
        }
        
        try {
            String url = properties.getServerUrl() + CANCEL_PATH + taskId;
            
            ResponseEntity<Result<Boolean>> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    null,
                    new ParameterizedTypeReference<Result<Boolean>>() {}
            );
            
            Result<Boolean> result = response.getBody();
            return result != null && result.getSuccess() && Boolean.TRUE.equals(result.getData());
        } catch (Exception e) {
            log.error("Failed to cancel retry task: {}", taskId, e);
            return false;
        }
    }
    
    @Override
    public RetryTaskDTO queryTask(String taskId) {
        // 开发模式下返回模拟数据
        if (properties.isDevMode()) {
            log.info("[DEV MODE] Query retry task: {}", taskId);
            RetryTaskDTO dto = new RetryTaskDTO();
            dto.setTaskId(taskId);
            dto.setTaskStatus("DEV_MODE");
            return dto;
        }
        
        if (taskId == null || taskId.isEmpty()) {
            throw new IllegalArgumentException("TaskId cannot be null or empty");
        }
        
        try {
            String url = properties.getServerUrl() + QUERY_PATH + taskId;
            
            ResponseEntity<Result<RetryTaskDTO>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<Result<RetryTaskDTO>>() {}
            );
            
            Result<RetryTaskDTO> result = response.getBody();
            if (result != null && result.getSuccess()) {
                return result.getData();
            } else {
                String errorMsg = result != null ? result.getMessage() : "Unknown error";
                throw new RuntimeException("Failed to query retry task: " + errorMsg);
            }
        } catch (Exception e) {
            log.error("Failed to query retry task: {}", taskId, e);
            throw new RuntimeException("Failed to query retry task", e);
        }
    }
    
    /**
     * 校验请求参数
     */
    private void validateRequest(RetryTaskRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("RetryTaskRequest cannot be null");
        }
        if (request.getSceneType() == null) {
            throw new IllegalArgumentException("SceneType cannot be null");
        }
        if (request.getIdempotentKey() == null || request.getIdempotentKey().isEmpty()) {
            throw new IllegalArgumentException("IdempotentKey cannot be null or empty");
        }
        if (request.getMethodClass() == null || request.getMethodClass().isEmpty()) {
            throw new IllegalArgumentException("MethodClass cannot be null or empty");
        }
        if (request.getMethodName() == null || request.getMethodName().isEmpty()) {
            throw new IllegalArgumentException("MethodName cannot be null or empty");
        }
    }
}

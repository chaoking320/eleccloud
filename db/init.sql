-- 创建数据库
CREATE DATABASE IF NOT EXISTS retry_platform DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE retry_platform;

-- 重试任务表
CREATE TABLE IF NOT EXISTS retry_task (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    task_id VARCHAR(64) UNIQUE NOT NULL COMMENT '任务ID',
    scene_type INT NOT NULL COMMENT '场景类型',
    idempotent_key VARCHAR(128) NOT NULL COMMENT '幂等键',
    method_class VARCHAR(256) NOT NULL COMMENT '方法类名',
    method_name VARCHAR(128) NOT NULL COMMENT '方法名',
    method_params TEXT COMMENT '方法参数JSON',
    task_status VARCHAR(20) NOT NULL COMMENT '任务状态: INIT/WAIT/SUCCESS/FAILED',
    retry_count INT DEFAULT 0 COMMENT '重试次数',
    max_retry_count INT NOT NULL COMMENT '最大重试次数',
    next_retry_time BIGINT COMMENT '下次重试时间戳(毫秒)',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_scene_idempotent (scene_type, idempotent_key),
    INDEX idx_status_next_time (task_status, next_retry_time),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='重试任务表';

-- 重试历史表
CREATE TABLE IF NOT EXISTS retry_history (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    task_id VARCHAR(64) NOT NULL COMMENT '任务ID',
    retry_count INT NOT NULL COMMENT '重试次数',
    execute_time DATETIME NOT NULL COMMENT '执行时间',
    execute_result VARCHAR(20) NOT NULL COMMENT '执行结果: SUCCESS/FAILED/RETRY',
    error_message TEXT COMMENT '错误信息',
    cost_time INT COMMENT '耗时(ms)',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_task_id (task_id),
    INDEX idx_execute_time (execute_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='重试历史表';

-- 场景配置表
CREATE TABLE IF NOT EXISTS scene_config (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    scene_type INT UNIQUE NOT NULL COMMENT '场景类型',
    scene_name VARCHAR(128) NOT NULL COMMENT '场景名称',
    retry_intervals VARCHAR(256) NOT NULL COMMENT '重试间隔(分钟),逗号分隔',
    max_retry_count INT NOT NULL COMMENT '最大重试次数',
    hook_class VARCHAR(256) COMMENT '钩子类名',
    enabled TINYINT DEFAULT 1 COMMENT '是否启用: 0-禁用, 1-启用',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='场景配置表';

-- 失败任务表
CREATE TABLE IF NOT EXISTS failed_task (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    task_id VARCHAR(64) UNIQUE NOT NULL COMMENT '任务ID',
    scene_type INT NOT NULL COMMENT '场景类型',
    idempotent_key VARCHAR(128) NOT NULL COMMENT '幂等键',
    method_class VARCHAR(256) NOT NULL COMMENT '方法类名',
    method_name VARCHAR(128) NOT NULL COMMENT '方法名',
    method_params TEXT COMMENT '方法参数JSON',
    retry_count INT NOT NULL COMMENT '重试次数',
    fail_reason TEXT COMMENT '失败原因',
    create_time DATETIME NOT NULL COMMENT '任务创建时间',
    fail_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '失败时间',
    INDEX idx_scene_type (scene_type),
    INDEX idx_fail_time (fail_time),
    INDEX idx_idempotent_key (idempotent_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='失败任务表';

-- 插入示例场景配置
INSERT INTO scene_config (scene_type, scene_name, retry_intervals, max_retry_count, hook_class, enabled) 
VALUES 
(1, '退款场景', '1,5,10,30', 4, 'com.retry.platform.example.RefundRetryHook', 1),
(2, '结算场景', '2,10,30,60', 4, 'com.retry.platform.example.SettlementRetryHook', 1)
ON DUPLICATE KEY UPDATE scene_name=VALUES(scene_name);

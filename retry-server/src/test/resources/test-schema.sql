-- 测试环境数据库初始化脚本
CREATE DATABASE IF NOT EXISTS retry_platform_test DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE retry_platform_test;

-- 重试任务表
CREATE TABLE IF NOT EXISTS retry_task (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    task_id VARCHAR(64) UNIQUE NOT NULL COMMENT '任务ID',
    scene_type INT NOT NULL COMMENT '场景类型',
    idempotent_key VARCHAR(128) NOT NULL COMMENT '幂等键',
    method_class VARCHAR(256) NOT NULL COMMENT '方法类名',
    method_name VARCHAR(128) NOT NULL COMMENT '方法名',
    method_params TEXT COMMENT '方法参数JSON',
    hook_class VARCHAR(256) COMMENT 'Hook类名',
    method_param_types VARCHAR(512) COMMENT '方法参数类型列表',
    task_status VARCHAR(20) NOT NULL COMMENT '任务状态',
    submit_mode VARCHAR(20) DEFAULT 'POST_FAIL' COMMENT '提交模式',
    retry_count INT DEFAULT 0 COMMENT '重试次数',
    max_retry_count INT NOT NULL COMMENT '最大重试次数',
    next_retry_time BIGINT COMMENT '下次重试时间戳',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_scene_idempotent (scene_type, idempotent_key),
    INDEX idx_status_next_time (task_status, next_retry_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 重试历史表
CREATE TABLE IF NOT EXISTS retry_history (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_id VARCHAR(64) NOT NULL,
    retry_count INT NOT NULL,
    execute_time DATETIME NOT NULL,
    execute_result VARCHAR(20) NOT NULL,
    error_message TEXT,
    cost_time INT,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_task_id (task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 场景配置表
CREATE TABLE IF NOT EXISTS scene_config (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    scene_type INT UNIQUE NOT NULL,
    scene_name VARCHAR(128) NOT NULL,
    retry_intervals VARCHAR(256),
    max_retry_count INT NOT NULL,
    backoff_strategy VARCHAR(30) DEFAULT 'CUSTOM',
    backoff_base INT DEFAULT 1,
    max_retry_duration INT DEFAULT 0,
    hook_class VARCHAR(256),
    client_app_url VARCHAR(256),
    enabled TINYINT DEFAULT 1,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 失败任务表
CREATE TABLE IF NOT EXISTS failed_task (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_id VARCHAR(64) UNIQUE NOT NULL,
    scene_type INT NOT NULL,
    idempotent_key VARCHAR(128) NOT NULL,
    method_class VARCHAR(256) NOT NULL,
    method_name VARCHAR(128) NOT NULL,
    method_params TEXT,
    retry_count INT NOT NULL,
    fail_reason TEXT,
    create_time DATETIME NOT NULL,
    fail_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_scene_type (scene_type),
    INDEX idx_fail_time (fail_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 插入测试场景配置
INSERT INTO scene_config (scene_type, scene_name, retry_intervals, max_retry_count, backoff_strategy, hook_class, enabled)
VALUES (999, '集成测试场景', '0,0,0', 3, 'CUSTOM', 'com.retry.platform.test.TestHook', 1);

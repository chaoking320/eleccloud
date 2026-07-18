-- =====================================================
-- 分布式重试平台 数据库初始化脚本
-- =====================================================
CREATE DATABASE IF NOT EXISTS retry_platform DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE retry_platform;

-- =====================================================
-- 重试任务表
-- =====================================================
CREATE TABLE IF NOT EXISTS retry_task (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    task_id VARCHAR(64) UNIQUE NOT NULL COMMENT '任务ID',
    scene_type INT NOT NULL COMMENT '场景类型',
    idempotent_key VARCHAR(128) NOT NULL COMMENT '幂等键',
    method_class VARCHAR(256) NOT NULL COMMENT '方法类名',
    method_name VARCHAR(128) NOT NULL COMMENT '方法名',
    method_params TEXT COMMENT '方法参数JSON',
    task_status VARCHAR(20) NOT NULL COMMENT '任务状态: INIT/WAIT/SUCCESS/FAILED',
    submit_mode VARCHAR(20) DEFAULT 'POST_FAIL' COMMENT '提交模式: POST_FAIL-失败后提交(默认), PRE_SUBMIT-执行前预注册',
    retry_count INT DEFAULT 0 COMMENT '重试次数',
    max_retry_count INT NOT NULL COMMENT '最大重试次数',
    next_retry_time BIGINT COMMENT '下次重试时间戳(毫秒)',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_scene_idempotent (scene_type, idempotent_key),
    INDEX idx_status_next_time (task_status, next_retry_time),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='重试任务表';

-- =====================================================
-- 重试历史表
-- =====================================================
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

-- =====================================================
-- 场景配置表
-- =====================================================
CREATE TABLE IF NOT EXISTS scene_config (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    scene_type INT UNIQUE NOT NULL COMMENT '场景类型',
    scene_name VARCHAR(128) NOT NULL COMMENT '场景名称',
    retry_intervals VARCHAR(256) COMMENT '重试间隔(分钟),逗号分隔。backoff_strategy=CUSTOM时生效',
    max_retry_count INT NOT NULL COMMENT '最大重试次数',
    backoff_strategy VARCHAR(30) DEFAULT 'CUSTOM' COMMENT '退避策略: CUSTOM/FIXED/LINEAR/EXPONENTIAL',
    backoff_base INT DEFAULT 1 COMMENT '退避基数(分钟)，FIXED/LINEAR/EXPONENTIAL策略使用',
    max_retry_duration INT DEFAULT 0 COMMENT '最大重试总时长(秒), 0=不限制',
    hook_class VARCHAR(256) COMMENT '钩子类全限定名',
    client_app_url VARCHAR(256) COMMENT '客户端应用URL（平台回调地址）',
    enabled TINYINT DEFAULT 1 COMMENT '是否启用: 0-禁用, 1-启用',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='场景配置表';

-- =====================================================
-- 失败任务表
-- =====================================================
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

-- =====================================================
-- 预置场景配置（3个Demo场景，覆盖3种退避策略和3种接入模式）
-- =====================================================

-- 场景1：电商退款（注解模式 @RetryableTask + CUSTOM自定义间隔策略）
-- 重试间隔：1分钟、5分钟、10分钟、30分钟（手动配置）
INSERT INTO scene_config (scene_type, scene_name, retry_intervals, max_retry_count,
    backoff_strategy, backoff_base, max_retry_duration,
    hook_class, client_app_url, enabled)
VALUES (1, '电商退款场景', '1,5,10,30', 4,
    'CUSTOM', 1, 3600,
    'com.retry.platform.example.hook.RefundRetryHook', 'http://retry-example:8082', 1)
ON DUPLICATE KEY UPDATE
    scene_name=VALUES(scene_name), retry_intervals=VALUES(retry_intervals),
    backoff_strategy=VALUES(backoff_strategy), backoff_base=VALUES(backoff_base),
    max_retry_duration=VALUES(max_retry_duration), client_app_url=VALUES(client_app_url);

-- 场景2：酒店结算（API模式 RetryClient.submit() + LINEAR线性递增策略）
-- 重试间隔：2分钟、4分钟、6分钟、8分钟、10分钟（base=2，每次+2分钟）
INSERT INTO scene_config (scene_type, scene_name, retry_intervals, max_retry_count,
    backoff_strategy, backoff_base, max_retry_duration,
    hook_class, client_app_url, enabled)
VALUES (2, '酒店结算场景', NULL, 5,
    'LINEAR', 2, 7200,
    'com.retry.platform.example.hook.SettlementRetryHook', 'http://retry-example:8082', 1)
ON DUPLICATE KEY UPDATE
    scene_name=VALUES(scene_name), retry_intervals=VALUES(retry_intervals),
    backoff_strategy=VALUES(backoff_strategy), backoff_base=VALUES(backoff_base),
    max_retry_duration=VALUES(max_retry_duration), client_app_url=VALUES(client_app_url);

-- 场景3：库存同步（预提交模式 preSubmit=true + EXPONENTIAL指数退避策略）
-- 重试间隔：1分钟、2分钟、4分钟、8分钟、16分钟（base=1，2^n倍增）
INSERT INTO scene_config (scene_type, scene_name, retry_intervals, max_retry_count,
    backoff_strategy, backoff_base, max_retry_duration,
    hook_class, client_app_url, enabled)
VALUES (3, '库存同步场景', NULL, 5,
    'EXPONENTIAL', 1, 0,
    'com.retry.platform.example.hook.InventoryRetryHook', 'http://retry-example:8082', 1)
ON DUPLICATE KEY UPDATE
    scene_name=VALUES(scene_name), retry_intervals=VALUES(retry_intervals),
    backoff_strategy=VALUES(backoff_strategy), backoff_base=VALUES(backoff_base),
    max_retry_duration=VALUES(max_retry_duration), client_app_url=VALUES(client_app_url);

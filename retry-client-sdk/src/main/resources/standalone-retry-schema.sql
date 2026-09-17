-- ============================================================
-- Standalone 模式建表 SQL
-- 在业务方自己的数据库中执行此脚本，无需部署独立的 retry-server。
--
-- 使用方式：
--   1. 将此文件复制到你的项目 db/ 目录下
--   2. 在你的业务数据库中执行：
--      mysql -u root -p your_db < standalone-retry-schema.sql
--   3. 在 application.yml 中配置：
--      retry:
--        client:
--          mode: standalone
--          scenes:
--            - scene-type: 1001
--              ...
--
-- 注意：表结构基于 retry-server 的 retry_task / retry_history，
--       额外扩展了 hook_class、backoff_strategy、retry_intervals、method_param_types
--       字段，以便 Standalone 模式下不再依赖 scene_config 表。
--       若将来升级到 Remote 模式，retry_task/retry_history 数据可以直接迁移。
-- ============================================================

-- ============================================================
-- 重试任务表（Standalone 扩展版，含场景策略字段）
-- ============================================================
CREATE TABLE IF NOT EXISTS retry_task (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    task_id         VARCHAR(64)  UNIQUE NOT NULL COMMENT '任务ID（格式：RT + 毫秒时间戳 + 8位UUID）',
    scene_type      INT          NOT NULL COMMENT '场景类型（与 @RetryableTask(sceneType=xxx) 对应）',
    idempotent_key  VARCHAR(128) NOT NULL COMMENT '业务幂等键（如订单ID、流水号）',
    method_class    VARCHAR(256) NOT NULL COMMENT '业务方法所在类的全限定名',
    method_name     VARCHAR(128) NOT NULL COMMENT '业务方法名',
    method_params   TEXT         COMMENT '方法参数JSON（key-value格式）',
    -- ↓ 从注解/YAML场景配置写入的策略字段（替代 scene_config 表）
    hook_class      VARCHAR(256) COMMENT '重试钩子类全限定名（可选，不配则直接反射重试）',
    method_param_types VARCHAR(512) COMMENT '参数类型列表（逗号分隔，用于精确定位重载方法）',
    backoff_strategy VARCHAR(30) DEFAULT 'CUSTOM' COMMENT '退避策略：CUSTOM/FIXED/LINEAR/EXPONENTIAL',
    backoff_base    INT          DEFAULT 1 COMMENT '退避基数（分钟），FIXED/LINEAR/EXPONENTIAL策略使用',
    retry_intervals VARCHAR(256) COMMENT '自定义重试间隔（分钟整数，逗号分隔），CUSTOM策略使用，如 "1,3,6,9"',
    -- ↑ 策略字段
    task_status     VARCHAR(20)  NOT NULL COMMENT '任务状态：INIT / WAIT / EXECUTING / SUCCESS / FAILED / CANCELLED',
    submit_mode     VARCHAR(20)  DEFAULT 'POST_FAIL' COMMENT '提交模式：POST_FAIL / PRE_SUBMIT',
    retry_count     INT          DEFAULT 0 COMMENT '已重试次数',
    max_retry_count INT          NOT NULL COMMENT '最大重试次数',
    next_retry_time BIGINT       COMMENT '下次重试时间戳（毫秒）',
    error_msg       VARCHAR(1024) COMMENT '最近一次执行失败的错误信息（用于问题排查）',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间（Watchdog 心跳刷新此字段）',
    UNIQUE KEY uk_scene_idempotent (scene_type, idempotent_key),
    INDEX idx_status_next_time (task_status, next_retry_time),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='重试任务表（Standalone 模式，含场景策略字段）';


-- ============================================================
-- 重试历史表（与 retry-server 保持一致）
-- ============================================================
CREATE TABLE IF NOT EXISTS retry_history (
    id             BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    task_id        VARCHAR(64)  NOT NULL COMMENT '任务ID',
    retry_count    INT          NOT NULL COMMENT '重试次数（第几次）',
    execute_time   DATETIME     NOT NULL COMMENT '执行时间',
    execute_result VARCHAR(20)  NOT NULL COMMENT '执行结果：SUCCESS / FAILED / PENDING',
    error_message  TEXT         COMMENT '失败时的错误信息',
    cost_time      INT          COMMENT '执行耗时（毫秒）',
    create_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_task_id (task_id),
    INDEX idx_execute_time (execute_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='重试历史表';

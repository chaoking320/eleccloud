package com.retry.platform.client.executor;

import com.retry.platform.client.api.RetryClient;
import com.retry.platform.client.api.RetryInternalClient;
import com.retry.platform.client.dto.RetryTaskDTO;
import com.retry.platform.client.hook.QueryResult;
import com.retry.platform.client.hook.RetryContext;
import com.retry.platform.client.hook.RetryHook;
import com.retry.platform.client.mq.RetryMessagePayload;
import com.retry.platform.client.mq.RetryMessageProducer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.HashMap;
import java.util.Map;

/**
 * =========================================================================================
 * SDK 本地重试与状态机执行引擎 (LocalRetryExecutor)
 * =========================================================================================
 *
 * <p><b>【定位与核心职责】</b><br>
 * 本类是 ElecCloud 客户端 SDK 的“执行中枢”。在微服务架构中，中央调度器或本地延迟队列
 * 只负责“何时唤醒任务”，而真正执行业务重试逻辑、反查第三方状态、操作本地数据库的动作，
 * 全部收敛在当前类中，在业务进程的本地线程池中安全运转。
 *
 * <p><b>【核心执行时序流转图】</b>
 * <pre>
 *                       [MQ 延时消息到期: 瘦消息 / 胖消息]
 *                                     │
 *                                     ▼
 *                     【模块一：入口解析与 DTO 转换】
 *                                     │
 *                                     ▼
 *                     【模块二：CAS 抢占行锁 (防止多节点并发)】
 *                          │ (抢锁失败: 退出，防重)
 *                          ▼ (抢锁成功: 进入 EXECUTING)
 *                     【模块三：Hook 状态机路由与驱动】
 *                         /          │          \
 *                        /           │           \
 *          checkStatus=SUCCESS  checkStatus=WAIT  checkStatus=INIT (或无Hook)
 *                     │              │             │
 *                     ▼              ▼             ▼
 *                [执行回调]     [发起主动反查]   【模块四：启动看门狗心跳】
 *                [标记成功]     doQuery() 成功?        │
 *                              /         \       【模块五：本地反射调用原方法】
 *                           是/           \否          │
 *                            ▼             ▼           ▼
 *                       [执行回调]   【模块六：退避延时计算与重新入队】
 *                       [标记成功]   (scheduleNext -> Redis ZSET)
 * </pre>
 *
 * <p><b>【内部 7 大核心职责板块分类】</b>
 * <ol>
 *   <li><b>模块一：调度消息入口 (Ingress)</b>：统一分发瘦消息与胖消息，解析为统一执行载荷。</li>
 *   <li><b>模块二：统一执行核心与 CAS 并发控制 (Execution Dispatch & CAS Lock)</b>：行锁原子加锁抢占、防多节点并发重入。</li>
 *   <li><b>模块三：三阶段 Hook 状态机控制器 (State Machine)</b>：驱动 Hook 的 checkStatus / doQuery / doCallback 分支路由。</li>
 *   <li><b>模块四：本地方法反射调用与看门狗心跳 (Method Invocation & Watchdog)</b>：守护线程防僵尸回收、ThreadLocal 隔离防 AOP 递归。</li>
 *   <li><b>模块五：退避延时计算与重新调度 (Backoff & Re-enqueue)</b>：多策略退避计算（固定/线性/指数/自定义分钟）、延时队列打包再投递。</li>
 *   <li><b>模块六：状态流转与历史审计底层封装 (State Transition & Auditing)</b>：CAS 状态更新、失败安全回滚、执行耗时埋点。</li>
 *   <li><b>模块七：反射工具箱、类型安全转换与上下文构建 (Reflection & Type Conversion)</b>：Spring ClassLoader 兼容、重载方法精确查找、参数名发现与类型安全还原。</li>
 * </ol>
 */
@Slf4j
public class LocalRetryExecutor {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private RetryInternalClient retryClient;

    @Autowired
    private RetryMessageProducer retryMessageProducer;

    // =========================================================================================
    // 模块一：调度消息入口 (Ingress) —— 统一分发瘦消息与胖消息
    // =========================================================================================

    /**
     * 【瘦消息入口】：仅携带 taskId
     * <p>适用场景：旧版 MQ 消息或网络带宽敏感场景。需要先向平台/本地 DB 反查一次任务全量数据。
     *
     * @param taskId 待处理的任务全局唯一ID
     */
    public void execute(String taskId) {
        log.info("[LocalRetryExecutor] Processing slim message: taskId={}", taskId);
        try {
            // 1. 从 server 获取任务最新状态（瘦消息路径的唯一一次 HTTP 查询）
            RetryTaskDTO task = ((RetryClient) retryClient).queryTask(taskId);
            if (task == null) {
                log.warn("[LocalRetryExecutor] Task not found or deleted on server. taskId={}", taskId);
                return;
            }
            // 防御拦截：终态直接退出
            if ("SUCCESS".equals(task.getTaskStatus()) || "FAILED".equals(task.getTaskStatus())) {
                log.info("[LocalRetryExecutor] Task already terminal: taskId={}, status={}", taskId, task.getTaskStatus());
                return;
            }
            // 转换为 payload 后走统一执行逻辑
            executeInternal(buildPayloadFromDTO(task));
        } catch (Exception e) {
            log.error("[LocalRetryExecutor] Exception in slim message path: taskId={}", taskId, e);
            rollbackToPending(taskId, e.getMessage());
        }
    }

    /**
     * 胖消息执行入口（直接携带执行上下文，无需先 HTTP 查询任务详情）
     * HTTP 调用减少：queryTask 调用被省掉，只剩 CAS + 状态更新 = 2~3 次。
     *
     * @param payload 完整的任务执行上下文（从 MQ 消息中反序列化而来）
     */
    public void executeWithPayload(RetryMessagePayload payload) {
        log.info("[LocalRetryExecutor] Processing fat message: taskId={}, retryCount={}",
                payload.getTaskId(), payload.getRetryCount());
        try {
            executeInternal(payload);
        } catch (Exception e) {
            log.error("[LocalRetryExecutor] Exception in fat message path: taskId={}", payload.getTaskId(), e);
            rollbackToPending(payload.getTaskId(), e.getMessage());
        }
    }

    // =========================================================================================
    // 模块二：统一执行核心与 CAS 并发控制 (Execution Dispatch & CAS Lock)
    // =========================================================================================

    /**
     * 统一执行逻辑（胖消息和瘦消息在此汇聚）
     * 核心步骤：
     * 1. 原子 CAS 锁定任务为 EXECUTING（防多节点重复执行）
     * 2. 组装 RetryContext
     * 3. 加载 Hook（未配置 Hook 则降级为直接反射重试）
     * 4. 执行 hook.checkStatus(context) 状态机判定
     */
    private void executeInternal(RetryMessagePayload payload) {
        String taskId = payload.getTaskId();

        // 1. CAS 抢占行锁：多微服务节点同时监听到相同消息时，仅有 1 台机器能抢占成功
        if (!markExecutingSafely(taskId)) {
            log.info("[LocalRetryExecutor] Failed to CAS lock task (another node may be processing): taskId={}", taskId);
            return;
        }

        // 2. 构建重试上下文（包含业务入参 Map）
        RetryContext context = buildContextFromPayload(payload);

        // 3. 定位 Hook Bean（优先类名查，次选 Spring 容器类型匹配）
        String hookClassName = payload.getHookClass();
        RetryHook hook = null;
        if (hookClassName != null && !hookClassName.trim().isEmpty()) {
            try {
                hook = getHookBean(hookClassName);
            } catch (Exception e) {
                log.error("[LocalRetryExecutor] Failed to load hook: {}", hookClassName, e);
            }
        }

        if (hook == null) {
            // hook 未配置或找不到 → 降级：直接反射调用原始业务方法重试（无幂等反查保护）
            log.warn("[LocalRetryExecutor] Hook not found or not configured for taskId={}, hookClass={}. " +
                    "Falling back to direct method retry.", taskId, hookClassName);
            handleInit(taskId, context, new NoOpRetryHook(), payload);
            return;
        }

        // 4. 核心三阶段状态机驱动
        com.retry.platform.client.hook.RetryStatus status = hook.checkStatus(context);
        log.info("[LocalRetryExecutor] checkStatus: taskId={}, status={}", taskId, status);

        if (status == null) {
            status = com.retry.platform.client.hook.RetryStatus.INIT;
        }

        switch (status) {
            case SUCCESS:
                // 阶段一分支：本地已是终态成功，直接触发回调并标记 SUCCESS
                handleSuccess(taskId, context, hook);
                break;
            case WAIT:
                // 阶段二分支：仍在处理中，发起 doQuery 主动反查
                handleWait(taskId, context, hook, payload);
                break;
            case INIT:
            default:
                // 阶段三分支：需重新调用原方法重试
                handleInit(taskId, context, hook, payload);
                break;
        }
    }

    // =========================================================================================
    // 模块三：三阶段 Hook 状态机控制器 (SUCCESS / WAIT / INIT 分支处理)
    // =========================================================================================

    /**
     * 分支 1：处理已成功状态 (SUCCESS)
     * <p>场景：checkStatus 检查发现业务已经完成（如文档已被物理删除、订单已支付）。
     */
    private void handleSuccess(String taskId, RetryContext context, RetryHook hook) {
        log.info("[LocalRetryExecutor] Task already SUCCESS. taskId={}", taskId);
        hook.doCallback(context, QueryResult.success("Already confirmed SUCCESS by checkStatus"));
        ((RetryClient) retryClient).markSuccess(taskId);
    }

    /**
     * 分支 2：处理中间等待状态 (WAIT)
     * <p>场景：下游外部系统（如 RagFlow、银行）正在异步处理中，需要主动调用 doQuery() 反查。
     * <ul>
     *   <li>反查成功：触发 doCallback() 并标记任务终态 SUCCESS</li>
     *   <li>反查未完成：记录 PENDING 历史，计算退避间隔推入下一轮 WAIT</li>
     *   <li>反查异常：记录 FAILED 历史，计算退避间隔推入下一轮 WAIT</li>
     * </ul>
     */
    private void handleWait(String taskId, RetryContext context, RetryHook hook, RetryMessagePayload payload) {
        log.info("[LocalRetryExecutor] Task in WAIT state. Triggering doQuery. taskId={}", taskId);
        long startTime = System.currentTimeMillis();
        try {
            QueryResult queryResult = hook.doQuery(context);
            long costMs = System.currentTimeMillis() - startTime;
            if (queryResult.isSuccess()) {
                // 防御性编程：处理可能的null值
                int currentCount = context.getRetryCount() != null ? context.getRetryCount() : 1;
                if (currentCount <= 0) {
                    currentCount = 1; // 保证至少为1
                }
                log.info("[LocalRetryExecutor] Query confirmed SUCCESS. Triggering doCallback. taskId={}, retryCount={}", taskId, currentCount);
                hook.doCallback(context, queryResult);
                updateRetryCountAndStatus(taskId, currentCount, "SUCCESS");
                safeRecordHistory(taskId, currentCount, "SUCCESS", null, costMs);
            } else {
                log.info("[LocalRetryExecutor] Query returned failure/pending. Rescheduling. taskId={}", taskId);
                // ★ M1 修复：记录 WAIT 查询未完成历史
                int currentCount = context.getRetryCount() != null ? context.getRetryCount() : 0;
                safeRecordHistory(taskId, currentCount, "PENDING", "doQuery returned not-success", costMs);
                scheduleNext(payload, "WAIT");
            }
        } catch (Exception e) {
            long costMs = System.currentTimeMillis() - startTime;
            log.error("[LocalRetryExecutor] Query failed. Rescheduling. taskId={}", taskId, e);
            // ★ M1 修复：记录 WAIT 查询异常历史
            int currentCount = context.getRetryCount() != null ? context.getRetryCount() : 0;
            safeRecordHistory(taskId, currentCount, "FAILED", e.getMessage(), costMs);
            scheduleNext(payload, "WAIT");
        }
    }

    // =========================================================================================
    // 模块四：本地方法反射调用与看门狗心跳 (Method Invocation & Watchdog)
    // =========================================================================================

    /**
     * 分支 3：处理初始重试状态 (INIT)
     * <p>核心动作：
     * <ol>
     *   <li>启动后台守护看门狗线程（Daemon Thread），每隔 30s 发送一次心跳 {@code touchHeartbeat}，
     *       刷新 DB/Redis 中的 {@code update_time}，防止长耗时任务被巡检调度器误判为僵尸任务。</li>
     *   <li>通过 Spring {@code ApplicationContext} 与 {@code ClassLoader} 反射获取业务目标 Bean。</li>
     *   <li>精确定位目标重载方法，根据 Spring 参数发现器还原入参，并进行类型安全转换。</li>
     *   <li>在 {@code ThreadLocal} 中设置重试标记 {@code IN_RETRY_CONTEXT}，避免触发 AOP 递归拦截导致死循环。</li>
     *   <li>调用原方法完成后，再次触发 {@code hook.doQuery()} 进行确认，视结果进入终态 SUCCESS 或中间态 WAIT。</li>
     * </ol>
     *
     * @param taskId  全局任务ID
     * @param context 重试上下文（包含入参与方法元数据）
     * @param hook    重试业务钩子
     * @param payload 调度载荷
     */
    private void handleInit(String taskId, RetryContext context, RetryHook hook, RetryMessagePayload payload) {
        log.info("[LocalRetryExecutor] Task in INIT state. Invoking local method. taskId={}", taskId);
        long startTime = System.currentTimeMillis();

        // 1. 启动守护看门狗（Watchdog）：防止耗时长的业务任务被 FallbackScheduler 误判为超时僵尸任务并重新捞起
        java.util.concurrent.ScheduledExecutorService watchdog =
                java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
                    Thread t = new Thread(r, "retry-watchdog-" + taskId);
                    t.setDaemon(true); // 设为守护线程：不阻止 JVM 正常停机
                    return t;
                });
        watchdog.scheduleAtFixedRate(() -> {
            try {
                // touchHeartbeat 刷新任务 update_time
                retryClient.touchHeartbeat(taskId);
            } catch (Exception e) {
                log.warn("[LocalRetryExecutor] Failed to update heartbeat for taskId={}", taskId);
            }
        }, 30, 30, java.util.concurrent.TimeUnit.SECONDS);

        try {
            // 2. 反射获取 Spring 容器中的业务 Bean
            // 关键细节：使用 applicationContext 的 ClassLoader，确保在各类容器/热加载环境下都能正确加载类
            ClassLoader contextCl = applicationContext.getClassLoader();
            Class<?> clazz = Class.forName(context.getMethodClass(), true, contextCl);
            Object targetBean = applicationContext.getBean(clazz);

            Map<String, Object> paramsMap = parseParamsJson(context.getMethodParamsJson());
            // 3. 精确定位目标方法（传入 methodParamTypes，支持按签名完全匹配重载方法）
            Method targetMethod = findMethod(clazz, context.getMethodName(), paramsMap,
                    context.getMethodParamTypes());
            if (targetMethod == null) {
                throw new NoSuchMethodException("Method not found: " + context.getMethodName());
            }

            // 4. 准备入参并按目标类型进行安全转换（支持原始类型、包装类型与 JSON 复合对象）
            Object[] args = prepareMethodArgs(targetMethod, paramsMap);
            targetMethod.setAccessible(true);

            // 5. 反射调用原业务方法（带防递归 AOP 上下文保护）
            try {
                // 设置标志位：告诉 @RetryableTask 切面当前调用由 SDK 调度，切勿重复入库拦截
                com.retry.platform.client.aspect.RetryableTaskAspect.IN_RETRY_CONTEXT.set(Boolean.TRUE);
                targetMethod.invoke(targetBean, args);
            } finally {
                // 必清理 ThreadLocal，防止线程池复用污染后续正常请求
                com.retry.platform.client.aspect.RetryableTaskAspect.IN_RETRY_CONTEXT.remove();
            }

            long costMs = System.currentTimeMillis() - startTime;
            log.info("[LocalRetryExecutor] Local method execution succeeded! Checking hook. taskId={}", taskId);

            // 6. 执行完业务方法后，立即调用 Hook 的 doQuery 确认真实下游状态
            QueryResult queryResult = null;
            try {
                queryResult = hook.doQuery(context);
            } catch (Exception qe) {
                log.warn("[LocalRetryExecutor] hook.doQuery threw exception: {}", qe.getMessage());
            }

            if (queryResult != null && queryResult.isSuccess()) {
                // 防御性校验
                int currentCount = context.getRetryCount() != null ? context.getRetryCount() : 1;
                if (currentCount <= 0) {
                    currentCount = 1;
                }
                log.info("[LocalRetryExecutor] Hook confirmed SUCCESS. Triggering doCallback & markSuccess. taskId={}, retryCount={}", taskId, currentCount);
                hook.doCallback(context, queryResult);
                updateRetryCountAndStatus(taskId, currentCount, "SUCCESS");
                safeRecordHistory(taskId, currentCount, "SUCCESS", "Method executed and hook confirmed SUCCESS", costMs);
            } else {
                // 下游尚未完成或需异步回调确认，进入 WAIT 状态等待下一轮反查
                log.info("[LocalRetryExecutor] Hook returned not-success/pending. Entering WAIT state. taskId={}", taskId);
                int currentCount = context.getRetryCount() != null ? context.getRetryCount() : 0;
                safeRecordHistory(taskId, currentCount, "WAIT", "Method executed, waiting for callback/query", costMs);
                scheduleNext(payload, "WAIT");
            }

        } catch (Exception e) {
            long costMs = System.currentTimeMillis() - startTime;
            int currentCount = context.getRetryCount() != null ? context.getRetryCount() : 1;
            if (currentCount <= 0) {
                currentCount = 1;
            }
            log.warn("[LocalRetryExecutor] Local method invocation failed: taskId={}, retryCount={}, error={}", 
                    taskId, currentCount, e.getMessage());
            safeRecordHistory(taskId, currentCount, "FAILED",
                    e.getCause() != null ? e.getCause().getMessage() : e.getMessage(), costMs);
            scheduleNext(payload, "INIT");
        } finally {
            // 无论执行成功或抛出异常，立即停止看门狗线程
            watchdog.shutdownNow();
        }
    }

    // =========================================================================================
    // 模块五：退避延时计算与重新调度 (Backoff Calculation & Re-enqueue)
    // =========================================================================================

    /**
     * 重新调度下一轮重试
     * <p>时序：
     * 1. 检查是否超出最大重试次数 {@code maxRetryCount}；超出则标记终态 FAILED。
     * 2. 计算下一轮递增后的重试次数与退避延时毫秒数。
     * 3. 同步更新任务在持久层的重试计数与状态。
     * 4. 重新打包胖消息，发送至 Redis 延时队列（ZSET）。
     *
     * @param payload      当前调度上下文
     * @param targetStatus 目标流转状态（如 WAIT 或 INIT）
     */
    private void scheduleNext(RetryMessagePayload payload, String targetStatus) {
        String taskId = payload.getTaskId();
        int currentCount = payload.getRetryCount() != null ? payload.getRetryCount() : 1;
        if (currentCount <= 0) {
            currentCount = 1;
        }

        // 1. 重试上限拦截：超出最大次数直接标记终态 FAILED
        Integer maxRetryCount = payload.getMaxRetryCount();
        if (maxRetryCount != null && maxRetryCount > 0 && currentCount >= maxRetryCount) {
            log.warn("[LocalRetryExecutor] Reached max retry count ({}/{}). Marking FAILED. taskId={}", 
                    currentCount, maxRetryCount, taskId);
            markFailed(taskId, "Exceeded max retry count limit");
            return;
        }

        int newRetryCount = currentCount + 1;
        // 2. 根据策略（FIXED, LINEAR, EXPONENTIAL, CUSTOM）计算下次延时
        long delayMs = calculateDelayMsFromPayload(payload, newRetryCount);

        // 3. 更新持久层计数与状态
        updateRetryCountAndStatus(taskId, currentCount, targetStatus != null ? targetStatus : "INIT");

        // 4. 打包胖消息重新投递到延时队列
        RetryMessagePayload nextPayload = clonePayloadWithNewCount(payload, newRetryCount);
        retryMessageProducer.sendDelayMessageWithPayload(nextPayload, delayMs);
        log.info("[LocalRetryExecutor] Scheduled next retry: taskId={}, nextRetryCount={}, targetStatus={}, delayMs={}", 
                taskId, newRetryCount, targetStatus, delayMs);
    }

    /**
     * 克隆 Payload 并更新重试计数（确保胖消息在队列中自洽流转，无需依赖远端全量查询）
     */
    private RetryMessagePayload clonePayloadWithNewCount(RetryMessagePayload payload, int newRetryCount) {
        return RetryMessagePayload.builder()
                .taskId(payload.getTaskId())
                .sceneType(payload.getSceneType())
                .idempotentKey(payload.getIdempotentKey())
                .methodClass(payload.getMethodClass())
                .methodName(payload.getMethodName())
                .methodParams(payload.getMethodParams())
                .methodParamTypes(payload.getMethodParamTypes())
                .hookClass(payload.getHookClass())
                .backoffStrategy(payload.getBackoffStrategy())
                .backoffBase(payload.getBackoffBase())
                .retryIntervals(payload.getRetryIntervals())
                .retryCount(newRetryCount)
                .maxRetryCount(payload.getMaxRetryCount())
                .build();
    }

    // =========================================================================================
    // 模块六：状态流转与历史审计底层封装 (State Transition & Auditing)
    // =========================================================================================

    /**
     * 安全记录重试执行历史（弱依赖：记录失败仅打日志，绝不阻断重试主流程）
     */
    private void safeRecordHistory(String taskId, int retryCount, String result, String errorMsg, long costMs) {
        try {
            retryClient.recordHistory(taskId, retryCount, result, errorMsg, costMs);
        } catch (Exception e) {
            log.warn("[LocalRetryExecutor] Failed to record history: taskId={}, error={}", taskId, e.getMessage());
        }
    }

    /**
     * CAS 抢占行锁：多微服务节点竞争时，仅有一个节点将状态从待处理变为 EXECUTING
     */
    private boolean markExecutingSafely(String taskId) {
        try {
            return retryClient.markExecuting(taskId);
        } catch (Exception e) {
            return false;
        }
    }

    private void updateTaskStatus(String taskId, String status) {
        try {
            retryClient.updateStatus(taskId, status);
        } catch (Exception e) {
            log.error("Failed to update status: {}", taskId, e);
        }
    }

    private void updateRetryCountAndStatus(String taskId, int count, String status) {
        try {
            retryClient.updateRetryCountAndStatus(taskId, count, status);
        } catch (Exception e) {
            log.error("Failed to update retry info: {}", taskId, e);
        }
    }

    private void rollbackToPending(String taskId, String errorMsg) {
        try {
            retryClient.rollbackToPending(taskId, errorMsg);
        } catch (Exception e) {
            log.error("Failed to rollback: {}", taskId, e);
        }
    }

    private void markFailed(String taskId, String reason) {
        try {
            retryClient.markFailed(taskId, reason);
        } catch (Exception e) {
            log.error("Failed to mark failed: {}", taskId, e);
        }
    }

    // =========================================================================================
    // 模块七：反射工具箱、类型安全转换与上下文构建 (Reflection & Type Conversion)
    // =========================================================================================

    /**
     * 加载 Hook Bean。
     * 业务方的 Hook 通常注册方式有两种：
     * 1. @Component("com.xxx.XxxHook") 显式指定全类名作为 Bean 名 → 按名字查
     * 2. @Component 用类型注册 → 遍历上下文寻找匹配的类
     * 彻底解决 ClassLoader 隔离导致的 Class.forName 找不到类的问题。
     */
    private RetryHook getHookBean(String hookClass) throws Exception {
        // 1. 优先尝试直接按 Bean 名查找
        try {
            Object bean = applicationContext.getBean(hookClass);
            if (bean instanceof RetryHook) {
                log.debug("[LocalRetryExecutor] Hook found by exact name: {}", hookClass);
                return (RetryHook) bean;
            }
        } catch (Exception ignored) {
        }
        
        // 2. 如果按名字找不到，直接遍历 Spring 容器中所有的 RetryHook 实例
        // 这样可以完全绕过 Class.forName，避免 ClassLoader 找不到类的问题
        Map<String, RetryHook> hookBeans = applicationContext.getBeansOfType(RetryHook.class);
        for (RetryHook bean : hookBeans.values()) {
            String beanClassName = bean.getClass().getName();
            // 注意：被 Spring AOP 代理的类名可能是 com.xxx.DemoHook$$EnhancerBySpringCGLIB$$...
            if (beanClassName.equals(hookClass) || beanClassName.startsWith(hookClass + "$$")) {
                log.debug("[LocalRetryExecutor] Hook found by scanning context types: {}", hookClass);
                return bean;
            }
        }
        
        throw new ClassNotFoundException("Cannot find RetryHook bean for class: " + hookClass);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseParamsJson(String paramsJson) throws Exception {
        if (paramsJson == null || paramsJson.trim().isEmpty()) {
            return new HashMap<>();
        }
        return com.retry.platform.client.util.JsonUtil.fromJson(paramsJson, Map.class);
    }

    /**
     * 定位方法。
     * <p>
     * 修复：原实现仅按参数数量匹配，无法处理重载方法（同名不同参数类型）。
     * 修复后：优先按参数类型签名精确匹配；仅当 methodParamTypes 缺失时才降级按参数数量匹配。
     *
     * @param clazz          目标类
     * @param methodName     方法名
     * @param paramsMap      参数名⇒参数值映射（用于降级匹配时按数量匹配）
     * @param methodParamTypes 逻号分隔的全限定参数类型，可为 null
     * @return 匹配的 Method，找不到返回 null
     */
    private Method findMethod(Class<?> clazz, String methodName,
                              Map<String, Object> paramsMap, String methodParamTypes) {
        // ① 优先：按参数类型签名精确匹配（解决重载歧义）
        if (methodParamTypes != null && !methodParamTypes.trim().isEmpty()) {
            try {
                String[] typeNames = methodParamTypes.split(",");
                Class<?>[] paramTypes = new Class<?>[typeNames.length];
                for (int i = 0; i < typeNames.length; i++) {
                    paramTypes[i] = resolveClass(typeNames[i].trim());
                }
                return clazz.getDeclaredMethod(methodName, paramTypes);
            } catch (NoSuchMethodException e) {
                log.warn("[findMethod] Precise match failed for {}#{} with types=[{}], falling back to count-match.",
                        clazz.getName(), methodName, methodParamTypes);
            } catch (ClassNotFoundException e) {
                log.warn("[findMethod] Failed to resolve param type class: {}", e.getMessage());
            }
        }

        // ② 降级：按参数数量匹配（兼容旧数据 / methodParamTypes 为空的情况）
        Method matched = null;
        int matchCount = 0;
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.getName().equals(methodName)
                    && method.getParameterCount() == paramsMap.size()) {
                matched = method;
                matchCount++;
            }
        }
        if (matchCount > 1) {
            log.warn("[findMethod] Ambiguous overload: found {} methods named '{}' with {} params in {}. "
                    + "Consider re-submitting to persist methodParamTypes for precise matching.",
                    matchCount, methodName, paramsMap.size(), clazz.getName());
        }
        return matched;
    }

    /**
     * 将基本类型名 / 全限定类名解析为 Class。
     */
    private Class<?> resolveClass(String typeName) throws ClassNotFoundException {
        switch (typeName) {
            case "boolean": return boolean.class;
            case "byte":    return byte.class;
            case "char":    return char.class;
            case "short":   return short.class;
            case "int":     return int.class;
            case "long":    return long.class;
            case "float":   return float.class;
            case "double":  return double.class;
            case "void":    return void.class;
            default:        return Class.forName(typeName, true, applicationContext.getClassLoader());
        }
    }

    private static final org.springframework.core.ParameterNameDiscoverer parameterNameDiscoverer = new org.springframework.core.DefaultParameterNameDiscoverer();

    /**
     * 按参数名从 Map 中还原方法入参。
     * <p>
     * 修复：移除了原来的‘参数名含 id/key 就用幂等键填充’的启发式规则——
     * 该规则将任意含“id”/“key”字符串的参数（如 buildingId、apiKey）误填为幂等键。
     * 修复2：使用 Spring 的 ParameterNameDiscoverer，防止未开启 -parameters 编译参数时，
     * 参数名变成 arg0, arg1 导致参数丢失的问题。
     */
    private Object[] prepareMethodArgs(Method method, Map<String, Object> paramsMap) throws Exception {
        Parameter[] parameters = method.getParameters();
        Object[] args = new Object[parameters.length];
        String[] paramNames = parameterNameDiscoverer.getParameterNames(method);
        
        for (int i = 0; i < parameters.length; i++) {
            String paramName = (paramNames != null && paramNames.length > i) ? paramNames[i] : parameters[i].getName();
            Class<?> paramType = parameters[i].getType();
            Object paramValue = paramsMap.get(paramName);
            if (paramValue == null && log.isDebugEnabled()) {
                log.debug("[prepareMethodArgs] No value found for param '{}' in paramsMap, will use null/zero.", paramName);
            }
            args[i] = convertType(paramValue, paramType);
        }
        return args;
    }

    private Object convertType(Object value, Class<?> targetType) throws Exception {
        if (value == null) return null;
        if (targetType.isInstance(value)) return value;
        if (targetType == String.class) return value.toString();
        if (targetType == Integer.class || targetType == int.class) {
            if (value instanceof Number) return ((Number) value).intValue();
            return Integer.parseInt(value.toString());
        }
        if (targetType == Long.class || targetType == long.class) {
            if (value instanceof Number) return ((Number) value).longValue();
            return Long.parseLong(value.toString());
        }
        if (targetType == Double.class || targetType == double.class) {
            if (value instanceof Number) return ((Number) value).doubleValue();
            return Double.parseDouble(value.toString());
        }
        if (targetType == Boolean.class || targetType == boolean.class) {
            if (value instanceof Boolean) return value;
            return Boolean.parseBoolean(value.toString());
        }
        String json = com.retry.platform.client.util.JsonUtil.toJson(value);
        return com.retry.platform.client.util.JsonUtil.fromJson(json, targetType);
    }

    private RetryMessagePayload buildPayloadFromDTO(RetryTaskDTO task) {
        return RetryMessagePayload.builder()
                .taskId(task.getTaskId())
                .sceneType(task.getSceneType())
                .idempotentKey(task.getIdempotentKey())
                .methodClass(task.getMethodClass())
                .methodName(task.getMethodName())
                .methodParams(task.getMethodParams())
                .methodParamTypes(task.getMethodParamTypes())
                .hookClass(task.getHookClass())
                .backoffStrategy(task.getBackoffStrategy())
                .backoffBase(task.getBackoffBase())
                .retryIntervals(task.getRetryIntervals())
                .retryCount(task.getRetryCount())
                .maxRetryCount(task.getMaxRetryCount())
                .build();
    }

    private long calculateDelayMsFromPayload(RetryMessagePayload payload, int retryCount) {
        String strategy = payload.getBackoffStrategy() != null ? payload.getBackoffStrategy() : "CUSTOM";
        com.retry.platform.client.strategy.BackoffType type;
        try {
            type = com.retry.platform.client.strategy.BackoffType.valueOf(strategy.toUpperCase());
        } catch (Exception e) {
            type = com.retry.platform.client.strategy.BackoffType.CUSTOM;
        }
        int baseMins = payload.getBackoffBase() != null ? payload.getBackoffBase() : 1;

        long delayMs;
        if (com.retry.platform.client.strategy.BackoffType.FIXED == type) {
            delayMs = baseMins * 60_000L;
        } else if (com.retry.platform.client.strategy.BackoffType.LINEAR == type) {
            delayMs = (long) retryCount * baseMins * 60_000L;
        } else if (com.retry.platform.client.strategy.BackoffType.EXPONENTIAL == type) {
            int exp = Math.min(retryCount - 1, 30);
            delayMs = baseMins * (1L << exp) * 60_000L;
        } else {
            // CUSTOM：retryIntervals 为逗号分隔的整数（分钟）
            String intervals = payload.getRetryIntervals();
            if (intervals == null || intervals.trim().isEmpty()) {
                log.warn("[LocalRetryExecutor] retryIntervals not configured, using default 1 min. taskId={}",
                        payload.getTaskId());
                delayMs = 60_000L;
            } else {
                String[] split = intervals.split(",");
                int idx = Math.min(Math.max(0, retryCount - 1), split.length - 1);
                try {
                    int minutes = Integer.parseInt(split[idx].trim());
                    delayMs = minutes * 60_000L;
                } catch (NumberFormatException e) {
                    log.error("[LocalRetryExecutor] Invalid retryIntervals value '{}' at index {}, must be integer minutes. " +
                            "Falling back to 1 min. taskId={}", split[idx].trim(), idx, payload.getTaskId());
                    delayMs = 60_000L;
                }
            }
        }

        // 最小延迟保护：至少 200ms，防止 0 分钟配置导致无保护的高频重试风暴
        if (delayMs < 200L) {
            delayMs = 200L;
        }
        return delayMs;
    }

    private RetryContext buildContextFromPayload(RetryMessagePayload payload) {
        Map<String, Object> paramsMap = null;
        try {
            if (payload.getMethodParams() != null && !payload.getMethodParams().trim().isEmpty()) {
                paramsMap = com.retry.platform.client.util.JsonUtil.fromJson(payload.getMethodParams(), Map.class);
            }
        } catch (Exception e) {
            log.error("Failed to parse method params: taskId={}", payload.getTaskId(), e);
        }
        return RetryContext.builder()
                .taskId(payload.getTaskId())
                .sceneType(payload.getSceneType())
                .idempotentKey(payload.getIdempotentKey())
                .params(paramsMap)
                .retryCount(payload.getRetryCount() != null ? payload.getRetryCount() : 0)
                .maxRetryCount(payload.getMaxRetryCount() != null ? payload.getMaxRetryCount() : 3)
                .methodClass(payload.getMethodClass())
                .methodName(payload.getMethodName())
                .methodParamsJson(payload.getMethodParams())
                .methodParamTypes(payload.getMethodParamTypes())
                .build();
    }

    /**
     * 无 Hook 时的默认实现：checkStatus 始终返回 INIT，触发直接反射重试原始方法。
     * doQuery 默认返回 success（业务方法执行无异常即视为成功），doCallback 不做任何事。
     */
    private static class NoOpRetryHook implements RetryHook {
        @Override
        public com.retry.platform.client.hook.RetryStatus checkStatus(RetryContext context) {
            return com.retry.platform.client.hook.RetryStatus.INIT;
        }
        @Override
        public QueryResult doQuery(RetryContext context) {
            // 无Hook场景下，业务方法执行成功（无异常）即视为任务成功
            return QueryResult.success("NoOpRetryHook: method executed successfully, no query needed");
        }
        @Override
        public void doCallback(RetryContext context, QueryResult result) {
            // do nothing
        }
    }
}


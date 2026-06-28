package com.retry.platform.server.metrics;

import com.retry.platform.server.mapper.FailedTaskMapper;
import com.retry.platform.server.mapper.RetryTaskMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.concurrent.TimeUnit;

/**
 * 分布式重试平台 Prometheus 指标监控埋点
 */
@Component
public class RetryMetrics {

    @Autowired
    private MeterRegistry registry;

    @Autowired
    private RetryTaskMapper retryTaskMapper;

    @Autowired
    private FailedTaskMapper failedTaskMapper;

    // 各种监控计数器/度量
    private Counter totalSubmittedCounter;
    private Timer executionTimer;

    @PostConstruct
    public void init() {
        // 注册活跃重试任务数 Gauge 指标
        Gauge.builder("retry.active.tasks.count", this, RetryMetrics::getActiveTasksCount)
             .description("The number of currently active retry tasks in the system")
             .register(registry);

        // 注册死信/失败任务数 Gauge 指标
        Gauge.builder("retry.failed.tasks.count", this, RetryMetrics::getFailedTasksCount)
             .description("The number of failed retry tasks in the system")
             .register(registry);
    }

    private double getActiveTasksCount() {
        try {
            return retryTaskMapper.countTasks(null, null);
        } catch (Exception e) {
            return 0.0;
        }
    }

    private double getFailedTasksCount() {
        try {
            return failedTaskMapper.countByConditions(null, null, null, null);
        } catch (Exception e) {
            return 0.0;
        }
    }

    /**
     * 埋点统计新提交任务数量
     */
    public void recordTaskSubmitted(Integer sceneType) {
        registry.counter("retry.tasks.submitted.total", 
                "sceneType", String.valueOf(sceneType))
                .increment();
    }

    /**
     * 埋点统计任务执行结果及次数
     */
    public void recordTaskExecuted(Integer sceneType, String result) {
        registry.counter("retry.tasks.executed.total", 
                "sceneType", String.valueOf(sceneType),
                "result", result)
                .increment();
    }

    /**
     * 埋点统计任务执行耗时
     */
    public void recordExecutionTime(Integer sceneType, long costTimeMs) {
        registry.timer("retry.tasks.execution.duration", 
                "sceneType", String.valueOf(sceneType))
                .record(costTimeMs, TimeUnit.MILLISECONDS);
    }
}

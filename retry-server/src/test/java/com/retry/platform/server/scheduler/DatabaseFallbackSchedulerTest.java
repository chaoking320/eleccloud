package com.retry.platform.server.scheduler;

import com.retry.platform.client.mq.RetryMessagePayload;
import com.retry.platform.client.mq.RetryMessageProducer;
import com.retry.platform.client.util.JsonUtil;
import com.retry.platform.server.entity.RetryTask;
import com.retry.platform.server.entity.SceneConfig;
import com.retry.platform.server.mapper.RetryTaskMapper;
import com.retry.platform.server.service.SceneConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DatabaseFallbackSchedulerTest {

    @Mock
    private RetryTaskMapper retryTaskMapper;

    @Mock
    private SceneConfigService sceneConfigService;

    @Mock
    private RetryMessageProducer retryMessageProducer;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ZSetOperations<String, String> zSetOperations;

    @InjectMocks
    private DatabaseFallbackScheduler scheduler = new DatabaseFallbackScheduler("retry.delayed.queue");

    private static final String DELAY_QUEUE_KEY = "retry:client:delay:queue:retry.delayed.queue";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(scheduler, "batchSize", 100);
        lenient().when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
    }

    @Test
    @DisplayName("当任务已在 Redis 中（胖消息）时，不应重复向 Redis 投递")
    void testTaskAlreadyInRedis_fatMessage_shouldNotResync() {
        String taskId = "TASK_FAT_001";
        RetryMessagePayload payload = RetryMessagePayload.builder().taskId(taskId).build();
        String fatMember = JsonUtil.toJson(payload);

        lenient().when(zSetOperations.range(eq(DELAY_QUEUE_KEY), eq(0L), eq(999L)))
                .thenReturn(Collections.singleton(fatMember));
        lenient().when(retryTaskMapper.selectPendingTasks(anyLong(), anyInt()))
                .thenReturn(Collections.singletonList(taskId));

        scheduler.scanAndSyncToRedis();

        verify(retryMessageProducer, never()).sendDelayMessageWithPayload(any(), anyLong());
    }

    @Test
    @DisplayName("当任务已在 Redis 中（瘦消息）时，不应重复向 Redis 投递")
    void testTaskAlreadyInRedis_slimMessage_shouldNotResync() {
        String taskId = "TASK_SLIM_001";

        lenient().when(zSetOperations.range(eq(DELAY_QUEUE_KEY), eq(0L), eq(999L)))
                .thenReturn(Collections.singleton(taskId));
        lenient().when(retryTaskMapper.selectPendingTasks(anyLong(), anyInt()))
                .thenReturn(Collections.singletonList(taskId));

        scheduler.scanAndSyncToRedis();

        verify(retryMessageProducer, never()).sendDelayMessageWithPayload(any(), anyLong());
    }

    @Test
    @DisplayName("当任务不在 Redis 中时，应从数据库提取并补发到 Redis")
    void testTaskNotInRedis_shouldResync() {
        String taskId = "TASK_MISSING_001";

        lenient().when(zSetOperations.range(eq(DELAY_QUEUE_KEY), eq(0L), eq(999L)))
                .thenReturn(Collections.emptySet());
        lenient().when(zSetOperations.score(eq(DELAY_QUEUE_KEY), eq(taskId))).thenReturn(null);

        when(retryTaskMapper.selectPendingTasks(anyLong(), anyInt()))
                .thenReturn(Collections.singletonList(taskId));

        RetryTask task = new RetryTask();
        task.setTaskId(taskId);
        task.setSceneType(1001);
        task.setTaskStatus("INIT");
        task.setRetryCount(0);
        task.setMaxRetryCount(3);
        when(retryTaskMapper.selectByTaskId(taskId)).thenReturn(task);

        SceneConfig sceneConfig = new SceneConfig();
        sceneConfig.setSceneType(1001);
        when(sceneConfigService.getSceneConfigByType(1001)).thenReturn(sceneConfig);

        scheduler.scanAndSyncToRedis();

        verify(retryMessageProducer, times(1)).sendDelayMessageWithPayload(
                argThat(p -> taskId.equals(p.getTaskId())), eq(0L));
    }
}

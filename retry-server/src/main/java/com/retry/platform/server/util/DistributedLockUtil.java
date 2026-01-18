package com.retry.platform.server.util;

import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 分布式锁工具类
 * 封装Redisson分布式锁的常用操作
 */
@Slf4j
public class DistributedLockUtil {
    
    /**
     * 使用分布式锁执行操作
     * 
     * @param redissonClient Redisson客户端
     * @param lockKey 锁的key
     * @param waitTime 等待获取锁的时间（秒）
     * @param leaseTime 锁持有时间（秒）
     * @param task 要执行的任务
     * @return 是否成功执行
     */
    public static boolean executeWithLock(RedissonClient redissonClient, 
                                         String lockKey,
                                         long waitTime,
                                         long leaseTime,
                                         Runnable task) {
        RLock lock = redissonClient.getLock(lockKey);
        
        try {
            boolean locked = lock.tryLock(waitTime, leaseTime, TimeUnit.SECONDS);
            
            if (!locked) {
                log.debug("Failed to acquire lock: lockKey={}", lockKey);
                return false;
            }
            
            try {
                task.run();
                return true;
            } finally {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Lock interrupted: lockKey={}", lockKey, e);
            return false;
        } catch (Exception e) {
            log.error("Failed to execute task with lock: lockKey={}", lockKey, e);
            throw new RuntimeException("Failed to execute task with lock", e);
        }
    }
    
    /**
     * 使用分布式锁执行操作（带返回值）
     * 
     * @param redissonClient Redisson客户端
     * @param lockKey 锁的key
     * @param waitTime 等待获取锁的时间（秒）
     * @param leaseTime 锁持有时间（秒）
     * @param task 要执行的任务
     * @param <T> 返回值类型
     * @return 任务执行结果，如果获取锁失败返回null
     */
    public static <T> T executeWithLock(RedissonClient redissonClient,
                                       String lockKey,
                                       long waitTime,
                                       long leaseTime,
                                       Supplier<T> task) {
        RLock lock = redissonClient.getLock(lockKey);
        
        try {
            boolean locked = lock.tryLock(waitTime, leaseTime, TimeUnit.SECONDS);
            
            if (!locked) {
                log.debug("Failed to acquire lock: lockKey={}", lockKey);
                return null;
            }
            
            try {
                return task.get();
            } finally {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Lock interrupted: lockKey={}", lockKey, e);
            return null;
        } catch (Exception e) {
            log.error("Failed to execute task with lock: lockKey={}", lockKey, e);
            throw new RuntimeException("Failed to execute task with lock", e);
        }
    }
    
    /**
     * 尝试获取锁并执行操作，如果获取失败则抛出异常
     * 
     * @param redissonClient Redisson客户端
     * @param lockKey 锁的key
     * @param waitTime 等待获取锁的时间（秒）
     * @param leaseTime 锁持有时间（秒）
     * @param task 要执行的任务
     * @throws RuntimeException 如果获取锁失败
     */
    public static void executeWithLockOrThrow(RedissonClient redissonClient,
                                             String lockKey,
                                             long waitTime,
                                             long leaseTime,
                                             Runnable task) {
        boolean success = executeWithLock(redissonClient, lockKey, waitTime, leaseTime, task);
        if (!success) {
            throw new RuntimeException("Failed to acquire lock: " + lockKey);
        }
    }
}

package com.shansuda.activity.service;

import com.shansuda.common.api.BizException;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;

/**
 * Redisson 临界区。auto/live 缺客户端时拒绝进入，禁止静默无锁。
 */
public final class CriticalSection {

    private CriticalSection() {
    }

    public static boolean lockRequired(String mode) {
        String m = mode == null ? "auto" : mode.trim().toLowerCase();
        return !"dry-run".equals(m);
    }

    public static void requireAvailable(RedissonClient client, String mode, Logger log) {
        if (client != null) {
            return;
        }
        if (!lockRequired(mode)) {
            if (log != null) {
                log.warn("dry-run 无 Redisson，临界区无锁执行（仅本地演示）");
            }
            return;
        }
        if (log != null) {
            log.error("Redisson 不可用，拒绝秒杀/抢券/抢单临界区。ssd.mode={} 生产必须配置 Redis/Redisson，禁止静默无锁", mode);
        }
        throw BizException.conflict("LOCK_UNAVAILABLE", "分布式锁不可用，拒绝进入临界区");
    }

    public static void run(RedissonClient client, String mode, String name, Runnable action, Logger log) {
        requireAvailable(client, mode, log);
        if (client == null) {
            action.run();
            return;
        }
        RLock lock = client.getLock(name);
        lock.lock();
        try {
            action.run();
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}

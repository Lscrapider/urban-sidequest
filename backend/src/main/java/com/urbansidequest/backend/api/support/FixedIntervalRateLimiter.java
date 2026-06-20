package com.urbansidequest.backend.api.support;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

/**
 * 固定最小间隔限流器，供各第三方 API 客户端按 QPS 约束控制请求节奏。
 *
 * <p>进程内单实例语义：通过 CAS 串行化"下一次允许调用"的时间戳，并用 {@link LockSupport#parkNanos}
 * 阻塞到允许时刻。多实例部署下的全局配额需另行处理，这里只保证单进程内的间隔约束。
 */
public final class FixedIntervalRateLimiter {

    private final long minIntervalNanos;

    private final AtomicLong nextAllowedAtNanos = new AtomicLong(0L);

    public FixedIntervalRateLimiter(long minIntervalNanos) {
        this.minIntervalNanos = minIntervalNanos;
    }

    public void acquire() {
        while (true) {
            long now = System.nanoTime();
            long current = this.nextAllowedAtNanos.get();
            long scheduled = Math.max(now, current);
            long next = scheduled + this.minIntervalNanos;
            if (this.nextAllowedAtNanos.compareAndSet(current, next)) {
                long waitNanos = scheduled - now;
                if (waitNanos > 0L) {
                    LockSupport.parkNanos(waitNanos);
                }
                return;
            }
        }
    }
}

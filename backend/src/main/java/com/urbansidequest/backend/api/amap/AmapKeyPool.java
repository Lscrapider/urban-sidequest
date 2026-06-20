package com.urbansidequest.backend.api.amap;

import com.urbansidequest.backend.config.AmapWebProperties;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

/**
 * 高德 Web Key 池，按 key 做 QPS 限速并对外屏蔽多 key 协调。
 *
 * <p>每个 key 持有一个独立的 Bucket4j 令牌桶（容量 1、按 {@code keyQps} 匀速回填，等价"每 1/qps 秒放行一次"），
 * 该 key 的所有高德接口调用共享这一份配额。API 层无需感知有几个 key、用哪个 key，只调用 {@link #acquireKey()}
 * "拿一个令牌"：池内部轮转遍历、用 {@code tryConsume} 非阻塞探测挑一个此刻有令牌的 key，全部繁忙时阻塞在轮转选中的 key 上。
 *
 * <p>因此对 API 层而言可用聚合 QPS = key 数 × {@code keyQps}（例如 3 个 key × 2.8 ≈ 8.4）。
 * 进程内单实例语义，不跨实例共享配额。
 */
@Component
public class AmapKeyPool {

    private static final double DEFAULT_KEY_QPS = 2.8;

    private final List<KeyBucket> keyBuckets;

    private final AtomicInteger cursor = new AtomicInteger();

    public AmapKeyPool(AmapWebProperties amapWebProperties) {
        double keyQps = amapWebProperties.getKeyQps() > 0 ? amapWebProperties.getKeyQps() : DEFAULT_KEY_QPS;
        long refillIntervalNanos = (long) (Duration.ofSeconds(1).toNanos() / keyQps);
        this.keyBuckets = amapWebProperties.effectiveKeys().stream()
                .map(key -> new KeyBucket(key, newBucket(refillIntervalNanos)))
                .toList();
    }

    public boolean isAvailable() {
        return !this.keyBuckets.isEmpty();
    }

    /**
     * 取一个可用 key 并消耗其一次配额；无可用 key 时阻塞等待最先空出的轮转 key。
     */
    public String acquireKey() {
        int size = this.keyBuckets.size();
        if (size == 0) {
            throw new IllegalStateException("未配置任何高德 Web Key");
        }
        int start = Math.floorMod(this.cursor.getAndIncrement(), size);
        for (int offset = 0; offset < size; offset++) {
            KeyBucket candidate = this.keyBuckets.get((start + offset) % size);
            if (candidate.bucket().tryConsume(1)) {
                return candidate.key();
            }
        }
        KeyBucket chosen = this.keyBuckets.get(start);
        try {
            chosen.bucket().asBlocking().consume(1);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("等待高德 Key 限速许可被中断", exception);
        }
        return chosen.key();
    }

    private static Bucket newBucket(long refillIntervalNanos) {
        Bandwidth limit = Bandwidth.builder()
                .capacity(1)
                .refillGreedy(1, Duration.ofNanos(refillIntervalNanos))
                .build();
        return Bucket.builder().addLimit(limit).build();
    }

    private record KeyBucket(String key, Bucket bucket) {
    }
}

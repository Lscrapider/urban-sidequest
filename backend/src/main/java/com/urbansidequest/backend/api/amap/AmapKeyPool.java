package com.urbansidequest.backend.api.amap;

import com.urbansidequest.backend.config.AmapWebProperties;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 高德 Web Key 池，按 key 做 QPS 限速并对外屏蔽多 key 协调。
 *
 * <p>每个 key 持有一个独立的 Bucket4j 令牌桶（容量 1、按 {@code keyQps} 匀速回填，等价"每 1/qps 秒放行一次"），
 * 该 key 的所有高德接口调用共享这一份配额。API 层无需感知有几个 key、用哪个 key，只调用 {@link #acquireKey()}
 * "拿一个令牌"：池内部轮转遍历、用 {@code tryConsume} 非阻塞探测挑一个此刻有令牌的 key，全部繁忙时阻塞在轮转选中的 key 上。
 *
 * <p>因此对 API 层而言可用聚合 QPS = 健康 key 数 × {@code keyQps}（例如 3 个 key × 2.8 ≈ 8.4；1 个 key 停用后自动降为 2 个 key）。
 * 进程内单实例限速，key 停用状态通过 Redis 做轻量持久化，避免服务重启后马上重复打到已耗尽或已失效的 key。
 */
@Component
public class AmapKeyPool {

    private static final Logger LOGGER = LoggerFactory.getLogger(AmapKeyPool.class);
    private static final double DEFAULT_KEY_QPS = 2.8;
    private static final String REDIS_DISABLED_KEY_PREFIX = "urban-sidequest:amap:key:disabled:";
    private static final ZoneId BEIJING_ZONE_ID = ZoneId.of("Asia/Shanghai");

    private final List<KeySlot> keySlots;

    private final Map<String, KeySlot> keySlotMap;

    private final StringRedisTemplate stringRedisTemplate;

    private final AtomicInteger cursor = new AtomicInteger();

    public AmapKeyPool(
            AmapWebProperties amapWebProperties,
            ObjectProvider<StringRedisTemplate> stringRedisTemplateProvider
    ) {
        double keyQps = amapWebProperties.getKeyQps() > 0 ? amapWebProperties.getKeyQps() : DEFAULT_KEY_QPS;
        long refillIntervalNanos = (long) (Duration.ofSeconds(1).toNanos() / keyQps);
        this.keySlots = amapWebProperties.effectiveKeys().stream()
                .map(key -> new KeySlot(key, fingerprint(key), newBucket(refillIntervalNanos)))
                .toList();
        this.keySlotMap = new HashMap<>();
        this.keySlots.forEach(keySlot -> this.keySlotMap.put(keySlot.key(), keySlot));
        this.stringRedisTemplate = stringRedisTemplateProvider.getIfAvailable();
    }

    public boolean isAvailable() {
        Instant now = Instant.now();
        return this.keySlots.stream().anyMatch(keySlot -> !this.isDisabled(keySlot, now));
    }

    public int configuredKeyCount() {
        return this.keySlots.size();
    }

    /**
     * 取一个可用 key 并消耗其一次配额；无可用健康 key 时抛出异常，健康 key 均繁忙时等待轮转选中的健康 key。
     */
    public String acquireKey() {
        int size = this.keySlots.size();
        if (size == 0) {
            throw new IllegalStateException("未配置任何高德 Web Key");
        }

        Instant now = Instant.now();
        int start = Math.floorMod(this.cursor.getAndIncrement(), size);
        KeySlot chosen = null;
        for (int offset = 0; offset < size; offset++) {
            KeySlot candidate = this.keySlots.get((start + offset) % size);
            if (this.isDisabled(candidate, now)) {
                continue;
            }
            if (chosen == null) {
                chosen = candidate;
            }
            if (candidate.bucket().tryConsume(1)) {
                return candidate.key();
            }
        }

        if (chosen == null) {
            throw new IllegalStateException("所有高德 Web Key 当前均不可用");
        }
        try {
            chosen.bucket().asBlocking().consume(1);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("等待高德 Key 限速许可被中断", exception);
        }
        return chosen.key();
    }

    public void disableKey(String key, AmapKeyFailureClassifier.Classification classification) {
        if (classification == null || !classification.shouldDisableKey()) {
            return;
        }
        KeySlot keySlot = this.keySlotMap.get(key);
        if (keySlot == null) {
            return;
        }

        Instant now = Instant.now();
        Instant disabledUntil = disabledUntil(now, classification.type());
        DisabledState disabledState = new DisabledState(classification.type(), disabledUntil, classification.reason());
        keySlot.disable(disabledState);
        this.writeDisabledState(keySlot, disabledState, now);
        LOGGER.warn(
                "高德 Web Key 已停用，fingerprint={}，type={}，disabledUntil={}，reason={}",
                keySlot.fingerprint(),
                classification.type(),
                disabledUntil,
                classification.reason()
        );
    }

    private static Bucket newBucket(long refillIntervalNanos) {
        Bandwidth limit = Bandwidth.builder()
                .capacity(1)
                .refillGreedy(1, Duration.ofNanos(refillIntervalNanos))
                .build();
        return Bucket.builder().addLimit(limit).build();
    }

    private boolean isDisabled(KeySlot keySlot, Instant now) {
        DisabledState disabledState = keySlot.disabledState();
        if (disabledState != null && disabledState.disabledUntil().isAfter(now)) {
            return true;
        }
        if (disabledState != null) {
            keySlot.enable();
        }

        this.refreshDisabledStateFromRedis(keySlot, now);
        disabledState = keySlot.disabledState();
        return disabledState != null && disabledState.disabledUntil().isAfter(now);
    }

    private void refreshDisabledStateFromRedis(KeySlot keySlot, Instant now) {
        if (this.stringRedisTemplate == null) {
            return;
        }
        try {
            String value = this.stringRedisTemplate.opsForValue().get(redisDisabledKey(keySlot));
            DisabledState disabledState = parseDisabledState(value);
            if (disabledState == null) {
                return;
            }
            if (disabledState.disabledUntil().isAfter(now)) {
                keySlot.disable(disabledState);
            } else {
                keySlot.enable();
                this.stringRedisTemplate.delete(redisDisabledKey(keySlot));
            }
        } catch (RuntimeException exception) {
            LOGGER.debug("读取高德 Web Key 停用状态失败，fingerprint={}", keySlot.fingerprint(), exception);
        }
    }

    private void writeDisabledState(KeySlot keySlot, DisabledState disabledState, Instant now) {
        if (this.stringRedisTemplate == null) {
            return;
        }
        Duration ttl = Duration.between(now, disabledState.disabledUntil());
        if (ttl.isZero() || ttl.isNegative()) {
            return;
        }
        try {
            this.stringRedisTemplate.opsForValue().set(
                    redisDisabledKey(keySlot),
                    disabledState.type().name() + "|" + disabledState.disabledUntil().toEpochMilli() + "|" + nullToEmpty(disabledState.reason()),
                    ttl
            );
        } catch (RuntimeException exception) {
            LOGGER.warn("写入高德 Web Key 停用状态失败，fingerprint={}", keySlot.fingerprint(), exception);
        }
    }

    private static DisabledState parseDisabledState(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String[] parts = value.split("\\|", 3);
        if (parts.length < 2) {
            return null;
        }
        try {
            AmapKeyFailureClassifier.FailureType type = AmapKeyFailureClassifier.FailureType.valueOf(parts[0]);
            if (type == AmapKeyFailureClassifier.FailureType.NONE) {
                return null;
            }
            Instant disabledUntil = Instant.ofEpochMilli(Long.parseLong(parts[1]));
            String reason = parts.length == 3 ? parts[2] : null;
            return new DisabledState(type, disabledUntil, reason);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static Instant disabledUntil(Instant now, AmapKeyFailureClassifier.FailureType failureType) {
        return switch (failureType) {
            case QUOTA_EXHAUSTED -> nextMonthQuotaReset(now);
            case NONE -> now;
        };
    }

    private static Instant nextMonthQuotaReset(Instant now) {
        ZonedDateTime zonedNow = ZonedDateTime.ofInstant(now, BEIJING_ZONE_ID);
        return zonedNow.plusMonths(1)
                .withDayOfMonth(1)
                .withHour(0)
                .withMinute(10)
                .withSecond(0)
                .withNano(0)
                .toInstant();
    }

    private static String redisDisabledKey(KeySlot keySlot) {
        return REDIS_DISABLED_KEY_PREFIX + keySlot.fingerprint();
    }

    private static String fingerprint(String key) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(key.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 6);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", exception);
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static final class KeySlot {

        private final String key;

        private final String fingerprint;

        private final Bucket bucket;

        private volatile DisabledState disabledState;

        private KeySlot(String key, String fingerprint, Bucket bucket) {
            this.key = key;
            this.fingerprint = fingerprint;
            this.bucket = bucket;
        }

        private String key() {
            return this.key;
        }

        private String fingerprint() {
            return this.fingerprint;
        }

        private Bucket bucket() {
            return this.bucket;
        }

        private DisabledState disabledState() {
            return this.disabledState;
        }

        private void disable(DisabledState disabledState) {
            this.disabledState = disabledState;
        }

        private void enable() {
            this.disabledState = null;
        }
    }

    private record DisabledState(
            AmapKeyFailureClassifier.FailureType type,
            Instant disabledUntil,
            String reason
    ) {
    }
}

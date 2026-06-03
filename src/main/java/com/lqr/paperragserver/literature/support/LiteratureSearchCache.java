package com.lqr.paperragserver.literature.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lqr.paperragserver.literature.model.LiteratureSearchResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * 文献搜索 Redis 缓存。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LiteratureSearchCache {

    static final String REDIS_KEY_PREFIX = "paper-rag:literature:search:v1:";
    static final String LOCK_REDIS_KEY_PREFIX = "paper-rag:literature:search:lock:v1:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 从 Redis 读取搜索响应；读取失败、缓存缺失或 JSON 不可用时统一按未命中处理。
     */
    public Optional<LiteratureSearchResponse> get(Key key) {
        if (key == null) {
            return Optional.empty();
        }
        String keyHash = keyHash(key);
        try {
            String payload = redisTemplate.opsForValue().get(redisKey(keyHash));
            if (payload == null || payload.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(payload, LiteratureSearchResponse.class));
        } catch (RuntimeException | JsonProcessingException ex) {
            log.warn("literature.search.cache.redis.read.failed keyHash={}", keyHash, ex);
            return Optional.empty();
        }
    }

    /**
     * 将搜索响应以 JSON 写入 Redis，并使用调用方传入的 TTL；写入失败不影响搜索结果返回。
     */
    public void put(Key key, LiteratureSearchResponse response, Duration ttl) {
        if (key == null || response == null || ttl == null || ttl.isZero() || ttl.isNegative()) {
            return;
        }
        String keyHash = keyHash(key);
        try {
            String payload = objectMapper.writeValueAsString(response);
            redisTemplate.opsForValue().set(redisKey(keyHash), payload, ttl);
        } catch (RuntimeException | JsonProcessingException ex) {
            log.warn("literature.search.cache.redis.write.failed keyHash={}", keyHash, ex);
        }
    }

    /**
     * 为指定缓存 Key 尝试获取短期重建锁，避免同一查询并发重复调用外部服务。
     */
    public Optional<LockHandle> tryAcquireLock(Key key, Duration lockTtl) {
        if (key == null || lockTtl == null || lockTtl.isZero() || lockTtl.isNegative()) {
            return Optional.empty();
        }
        String keyHash = keyHash(key);
        String lockKey = lockRedisKey(keyHash);
        String lockValue = UUID.randomUUID().toString();
        try {
            Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey, lockValue, lockTtl);
            if (Boolean.TRUE.equals(locked)) {
                return Optional.of(new LockHandle(lockKey, lockValue));
            }
            return Optional.empty();
        } catch (RuntimeException ex) {
            log.warn("literature.search.cache.redis.lock.acquire.failed keyHash={}", keyHash, ex);
            return Optional.empty();
        }
    }

    /**
     * 释放当前实例持有的缓存重建锁，避免误删其他请求新建的锁。
     */
    public void releaseLock(LockHandle lockHandle) {
        if (lockHandle == null) {
            return;
        }
        try {
            String currentValue = redisTemplate.opsForValue().get(lockHandle.lockKey());
            if (lockHandle.lockValue().equals(currentValue)) {
                redisTemplate.delete(lockHandle.lockKey());
            }
        } catch (RuntimeException ex) {
            log.warn("literature.search.cache.redis.lock.release.failed lockKey={}", lockHandle.lockKey(), ex);
        }
    }

    /**
     * 根据业务缓存 Key 生成带版本命名空间的 Redis key，供缓存访问和测试断言复用。
     */
    String redisKey(Key key) {
        return redisKey(keyHash(key));
    }

    /**
     * 根据规范化后的缓存 Key 生成稳定摘要，避免 Redis key 暴露完整查询内容。
     */
    String keyHash(Key key) {
        return sha256Hex(hashInput(key));
    }

    /**
     * 拼接 Redis key 前缀和摘要后缀，隔离文献搜索缓存的命名空间。
     */
    private String redisKey(String keyHash) {
        return REDIS_KEY_PREFIX + keyHash;
    }

    /**
     * 拼接锁 Redis key 前缀和摘要后缀，隔离缓存重建锁的命名空间。
     */
    private String lockRedisKey(String keyHash) {
        return LOCK_REDIS_KEY_PREFIX + keyHash;
    }

    /**
     * 将参与缓存命中的字段转换为稳定文本，作为 SHA-256 的唯一输入来源。
     */
    private String hashInput(Key key) {
        StringBuilder builder = new StringBuilder();
        appendField(builder, "query", key.query());
        appendField(builder, "limit", Integer.toString(key.limit()));
        appendField(builder, "sortBy", key.sortBy());
        appendListField(builder, "categories", key.categories());
        appendField(builder, "dateFrom", key.dateFrom());
        appendField(builder, "dateTo", key.dateTo());
        return builder.toString();
    }

    /**
     * 追加单值字段，并写入长度信息，避免不同字段组合产生歧义文本。
     */
    private void appendField(StringBuilder builder, String name, String value) {
        builder.append(name).append('=');
        if (value == null) {
            builder.append("<null>");
        } else {
            builder.append(value.length()).append(':').append(value);
        }
        builder.append('\n');
    }

    /**
     * 追加列表字段，并保持列表当前顺序参与摘要生成。
     */
    private void appendListField(StringBuilder builder, String name, List<String> values) {
        builder.append(name).append('=').append(values.size()).append('\n');
        for (String value : values) {
            appendField(builder, "-", value);
        }
    }

    /**
     * 生成 SHA-256 十六进制摘要；运行环境缺少 SHA-256 时视为不可恢复配置错误。
     */
    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm is unavailable", ex);
        }
    }

    public record LockHandle(
            String lockKey,
            String lockValue
    ) {
    }

    public record Key(
            String query,
            int limit,
            String sortBy,
            List<String> categories,
            String dateFrom,
            String dateTo
    ) {
        /**
         * 构造缓存 Key 时固定 categories 表达，保证同一组分类不受输入顺序、大小写和空白影响。
         */
        public Key {
            categories = canonicalCategories(categories);
        }

        /**
         * 将分类列表规范化为稳定、不可变、可参与缓存摘要的形式。
         */
        private static List<String> canonicalCategories(List<String> categories) {
            if (categories == null || categories.isEmpty()) {
                return List.of();
            }
            return categories.stream()
                    .filter(category -> category != null && !category.isBlank())
                    .map(category -> category.trim().toLowerCase(Locale.ROOT))
                    .distinct()
                    .sorted()
                    .toList();
        }
    }
}

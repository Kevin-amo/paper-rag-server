package com.lqr.paperragserver.literature.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lqr.paperragserver.literature.model.LiteratureSearchResponse;
import com.lqr.paperragserver.literature.model.LiteratureSearchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class LiteratureSearchCacheTest {

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private LiteratureSearchCache cache;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        cache = new LiteratureSearchCache(redisTemplate, objectMapper);
    }

    @Test
    void putShouldWriteJsonAndGetShouldReadResponse() throws Exception {
        LiteratureSearchCache.Key key = cacheKey(List.of("cs.AI"));
        LiteratureSearchResponse response = response("Graph RAG");
        Duration ttl = Duration.ofMinutes(20);
        String redisKey = cache.redisKey(key);

        cache.put(key, response, ttl);

        verify(valueOperations).set(eq(redisKey), org.mockito.ArgumentMatchers.argThat(payload -> payload.contains("Graph RAG")), eq(ttl));
        String payload = objectMapper.writeValueAsString(response);
        when(valueOperations.get(redisKey)).thenReturn(payload);

        assertThat(cache.get(key)).contains(response);
    }

    @Test
    void putShouldPassTtlToRedis() {
        LiteratureSearchCache.Key key = cacheKey(List.of("cs.AI"));
        LiteratureSearchResponse response = response("Graph RAG");
        Duration ttl = Duration.ofMinutes(7);

        cache.put(key, response, ttl);

        verify(valueOperations).set(eq(cache.redisKey(key)), any(String.class), eq(ttl));
    }

    @Test
    void getShouldReturnEmptyWhenPayloadIsInvalidJson() {
        LiteratureSearchCache.Key key = cacheKey(List.of("cs.AI"));
        when(valueOperations.get(cache.redisKey(key))).thenReturn("not-json");

        assertThat(cache.get(key)).isEmpty();
    }

    @Test
    void getShouldReturnEmptyWhenRedisReadFails() {
        LiteratureSearchCache.Key key = cacheKey(List.of("cs.AI"));
        when(valueOperations.get(cache.redisKey(key))).thenThrow(new RedisConnectionFailureException("redis down"));

        assertThat(cache.get(key)).isEmpty();
    }

    @Test
    void putShouldNotThrowWhenRedisWriteFails() {
        LiteratureSearchCache.Key key = cacheKey(List.of("cs.AI"));
        doThrow(new RedisConnectionFailureException("redis down"))
                .when(valueOperations)
                .set(eq(cache.redisKey(key)), any(String.class), any(Duration.class));

        assertThatCode(() -> cache.put(key, response("Graph RAG"), Duration.ofMinutes(20)))
                .doesNotThrowAnyException();
    }

    @Test
    void redisKeyShouldBeStableForEquivalentCategorySets() {
        LiteratureSearchCache.Key first = cacheKey(List.of(" cs.AI ", "math.OC", "CS.AI", ""));
        LiteratureSearchCache.Key second = cacheKey(List.of("math.oc", "cs.ai"));

        assertThat(first.categories()).containsExactly("cs.ai", "math.oc");
        assertThat(cache.redisKey(first)).isEqualTo(cache.redisKey(second));
    }

    @Test
    void redisKeyShouldUseVersionedPrefixAndNotExposeRawQuery() {
        LiteratureSearchCache.Key key = new LiteratureSearchCache.Key(
                "retrieval augmented generation private query",
                10,
                "relevance",
                List.of("cs.AI"),
                "2024-01-01",
                "2024-12-31"
        );

        String redisKey = cache.redisKey(key);

        assertThat(redisKey).startsWith(LiteratureSearchCache.REDIS_KEY_PREFIX);
        assertThat(redisKey).doesNotContain("retrieval augmented generation private query");
    }

    @Test
    void tryAcquireLockShouldReturnHandleWhenSetIfAbsentSucceeds() {
        LiteratureSearchCache.Key key = cacheKey(List.of("cs.AI"));
        Duration lockTtl = Duration.ofSeconds(10);
        when(valueOperations.setIfAbsent(any(String.class), any(String.class), eq(lockTtl))).thenReturn(true);

        var lockHandle = cache.tryAcquireLock(key, lockTtl);

        assertThat(lockHandle).isPresent();
        assertThat(lockHandle.get().lockKey()).startsWith(LiteratureSearchCache.LOCK_REDIS_KEY_PREFIX);
        assertThat(lockHandle.get().lockValue()).isNotBlank();
        verify(valueOperations).setIfAbsent(eq(lockHandle.get().lockKey()), eq(lockHandle.get().lockValue()), eq(lockTtl));
    }

    @Test
    void tryAcquireLockShouldReturnEmptyWhenSetIfAbsentFails() {
        LiteratureSearchCache.Key key = cacheKey(List.of("cs.AI"));
        Duration lockTtl = Duration.ofSeconds(10);
        when(valueOperations.setIfAbsent(any(String.class), any(String.class), eq(lockTtl))).thenReturn(false);

        assertThat(cache.tryAcquireLock(key, lockTtl)).isEmpty();
    }

    @Test
    void releaseLockShouldDeleteWhenTokenMatches() {
        LiteratureSearchCache.LockHandle lockHandle = new LiteratureSearchCache.LockHandle(
                LiteratureSearchCache.LOCK_REDIS_KEY_PREFIX + "abc",
                "token-1"
        );
        when(valueOperations.get(lockHandle.lockKey())).thenReturn(lockHandle.lockValue());

        cache.releaseLock(lockHandle);

        verify(redisTemplate).delete(lockHandle.lockKey());
    }

    @Test
    void releaseLockShouldNotDeleteWhenTokenDoesNotMatch() {
        LiteratureSearchCache.LockHandle lockHandle = new LiteratureSearchCache.LockHandle(
                LiteratureSearchCache.LOCK_REDIS_KEY_PREFIX + "abc",
                "token-1"
        );
        when(valueOperations.get(lockHandle.lockKey())).thenReturn("token-2");

        cache.releaseLock(lockHandle);

        verify(redisTemplate, never()).delete(lockHandle.lockKey());
    }

    @Test
    void lockOperationsShouldNotThrowWhenRedisFails() {
        LiteratureSearchCache.Key key = cacheKey(List.of("cs.AI"));
        Duration lockTtl = Duration.ofSeconds(10);
        when(valueOperations.setIfAbsent(any(String.class), any(String.class), eq(lockTtl)))
                .thenThrow(new RedisConnectionFailureException("redis down"));
        LiteratureSearchCache.LockHandle lockHandle = new LiteratureSearchCache.LockHandle(
                LiteratureSearchCache.LOCK_REDIS_KEY_PREFIX + "abc",
                "token-1"
        );
        when(valueOperations.get(lockHandle.lockKey())).thenThrow(new RedisConnectionFailureException("redis down"));

        assertThatCode(() -> cache.tryAcquireLock(key, lockTtl)).doesNotThrowAnyException();
        assertThat(cache.tryAcquireLock(key, lockTtl)).isEmpty();
        assertThatCode(() -> cache.releaseLock(lockHandle)).doesNotThrowAnyException();
    }

    private LiteratureSearchCache.Key cacheKey(List<String> categories) {
        return new LiteratureSearchCache.Key(
                "retrieval augmented generation",
                10,
                "relevance",
                categories,
                "2024-01-01",
                "2024-12-31"
        );
    }

    private LiteratureSearchResponse response(String title) {
        return new LiteratureSearchResponse(List.of(new LiteratureSearchResult(
                title,
                List.of("Alice"),
                "Abstract",
                2024,
                "2024-01-01",
                null,
                List.of("Artificial Intelligence"),
                "Artificial Intelligence",
                "https://doi.org/10.1000/test",
                "https://example.org/paper",
                "https://example.org/paper.pdf",
                "openalex",
                "https://openalex.org/W123"
        )));
    }
}
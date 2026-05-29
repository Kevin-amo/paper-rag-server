package com.lqr.paperragserver.literature.support;

import com.lqr.paperragserver.literature.model.LiteratureSearchResponse;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 文献搜索内存缓存。
 */
@Component
public class LiteratureSearchCache {

    private final Clock clock;
    private final Map<Key, Entry> entries = new ConcurrentHashMap<>();

    public LiteratureSearchCache() {
        this(Clock.systemUTC());
    }

    LiteratureSearchCache(Clock clock) {
        this.clock = clock;
    }

    public Optional<LiteratureSearchResponse> get(Key key) {
        Entry entry = entries.get(key);
        if (entry == null) {
            return Optional.empty();
        }
        if (entry.expiresAt().isBefore(Instant.now(clock))) {
            entries.remove(key, entry);
            return Optional.empty();
        }
        return Optional.of(entry.response());
    }

    public void put(Key key, LiteratureSearchResponse response, Duration ttl) {
        if (key == null || response == null || ttl == null || ttl.isZero() || ttl.isNegative()) {
            return;
        }
        entries.put(key, new Entry(response, Instant.now(clock).plus(ttl)));
    }

    public record Key(
            String query,
            int limit,
            String sortBy,
            List<String> categories,
            String dateFrom,
            String dateTo
    ) {
        public Key {
            categories = categories == null ? List.of() : List.copyOf(categories);
        }
    }

    private record Entry(LiteratureSearchResponse response, Instant expiresAt) {
    }
}
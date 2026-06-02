package com.lqr.paperragserver.literature.service;

import com.lqr.paperragserver.common.logging.LogSanitizer;
import com.lqr.paperragserver.literature.client.OpenAlexLiteratureClient;
import com.lqr.paperragserver.literature.config.LiteratureSearchProperties;
import com.lqr.paperragserver.literature.exception.LiteratureSearchException;
import com.lqr.paperragserver.literature.model.LiteratureSearchRequest;
import com.lqr.paperragserver.literature.model.LiteratureSearchResponse;
import com.lqr.paperragserver.literature.model.LiteratureSearchResult;
import com.lqr.paperragserver.literature.support.LiteratureSearchCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 文献搜索业务入口。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LiteratureSearchService {

    private static final String SORT_RELEVANCE = "relevance";
    private static final String SORT_DATE = "date";
    private static final String OPENALEX_DISABLED_MESSAGE = "OpenAlex 文献搜索未启用";
    private static final String ALL_SOURCES_UNAVAILABLE = "外部文献服务暂不可用，请稍后重试";

    private final LiteratureSearchProperties literatureProperties;
    private final OpenAlexLiteratureClient openAlexLiteratureClient;
    private final LiteratureSearchCache cache;

    public LiteratureSearchResponse search(LiteratureSearchRequest request) {
        long startNanos = System.nanoTime();
        int limit = resolveLimit(request.limit());
        String sortBy = resolveSortBy(request.sortBy());
        List<String> categories = normalizeCategories(request.categories());
        String query = normalizeQuery(request.query());
        String dateFrom = normalizeText(request.dateFrom());
        String dateTo = normalizeText(request.dateTo());
        LiteratureSearchCache.Key cacheKey = new LiteratureSearchCache.Key(query, limit, sortBy, categories, dateFrom, dateTo);
        log.info("literature.search.start queryExcerpt={} limit={} sortBy={} dateFrom={} dateTo={} categoryCount={} cacheEnabled={}",
                LogSanitizer.safeExcerpt(query, 160), limit, sortBy, dateFrom, dateTo, categories.size(), literatureProperties.cache().isEnabled());

        if (literatureProperties.cache().isEnabled()) {
            var cached = cache.get(cacheKey);
            if (cached.isPresent()) {
                log.info("literature.search.cache.hit queryExcerpt={} limit={} sortBy={} dateFrom={} dateTo={} resultCount={} costMs={}",
                        LogSanitizer.safeExcerpt(query, 160), limit, sortBy, dateFrom, dateTo, cached.get().items().size(), elapsedMs(startNanos));
                return cached.get();
            }
            log.info("literature.search.cache.miss queryExcerpt={} limit={} sortBy={} dateFrom={} dateTo={}",
                    LogSanitizer.safeExcerpt(query, 160), limit, sortBy, dateFrom, dateTo);
            return searchWithCacheRebuildGuard(request, cacheKey, query, categories, dateFrom, dateTo, sortBy, limit, startNanos);
        }

        return searchOpenAlexAndCache(request, cacheKey, query, categories, dateFrom, dateTo, sortBy, limit, startNanos);
    }

    private LiteratureSearchResponse searchWithCacheRebuildGuard(
            LiteratureSearchRequest request,
            LiteratureSearchCache.Key cacheKey,
            String query,
            List<String> categories,
            String dateFrom,
            String dateTo,
            String sortBy,
            int limit,
            long startNanos
    ) {
        Optional<LiteratureSearchCache.LockHandle> lockHandle = cache.tryAcquireLock(cacheKey, literatureProperties.cache().lockTtl());
        if (lockHandle.isPresent()) {
            try {
                var cached = cache.get(cacheKey);
                if (cached.isPresent()) {
                    log.info("literature.search.cache.hit.after_lock limit={} sortBy={} dateFrom={} dateTo={} resultCount={} costMs={}",
                            limit, sortBy, dateFrom, dateTo, cached.get().items().size(), elapsedMs(startNanos));
                    return cached.get();
                }
                return searchOpenAlexAndCache(request, cacheKey, query, categories, dateFrom, dateTo, sortBy, limit, startNanos);
            } finally {
                cache.releaseLock(lockHandle.get());
            }
        }

        var waited = waitForRebuiltCache(cacheKey, limit, sortBy, dateFrom, dateTo, startNanos);
        if (waited.isPresent()) {
            return waited.get();
        }
        return searchOpenAlexAndCache(request, cacheKey, query, categories, dateFrom, dateTo, sortBy, limit, startNanos);
    }

    private Optional<LiteratureSearchResponse> waitForRebuiltCache(
            LiteratureSearchCache.Key cacheKey,
            int limit,
            String sortBy,
            String dateFrom,
            String dateTo,
            long startNanos
    ) {
        LiteratureSearchProperties.Cache cacheProperties = literatureProperties.cache();
        for (int attempt = 0; attempt < cacheProperties.waitMaxAttempts(); attempt++) {
            try {
                Thread.sleep(cacheProperties.waitRetryInterval().toMillis());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                log.warn("literature.search.cache.wait.interrupted limit={} sortBy={} dateFrom={} dateTo={} attempt={} costMs={}",
                        limit, sortBy, dateFrom, dateTo, attempt + 1, elapsedMs(startNanos));
                return Optional.empty();
            }
            var cached = cache.get(cacheKey);
            if (cached.isPresent()) {
                log.info("literature.search.cache.hit.after_wait limit={} sortBy={} dateFrom={} dateTo={} attempt={} resultCount={} costMs={}",
                        limit, sortBy, dateFrom, dateTo, attempt + 1, cached.get().items().size(), elapsedMs(startNanos));
                return cached;
            }
        }
        return Optional.empty();
    }

    private LiteratureSearchResponse searchOpenAlexAndCache(
            LiteratureSearchRequest request,
            LiteratureSearchCache.Key cacheKey,
            String query,
            List<String> categories,
            String dateFrom,
            String dateTo,
            String sortBy,
            int limit,
            long startNanos
    ) {
        if (!literatureProperties.openalex().isEnabled()) {
            log.warn("literature.search.fallback queryExcerpt={} limit={} sortBy={} dateFrom={} dateTo={} reason=OPENALEX_DISABLED",
                    LogSanitizer.safeExcerpt(query, 160), limit, sortBy, dateFrom, dateTo);
            throw new LiteratureSearchException(HttpStatus.SERVICE_UNAVAILABLE, "OPENALEX_DISABLED", OPENALEX_DISABLED_MESSAGE);
        }

        try {
            int fetchLimit = resolveFetchLimit(limit, sortBy);
            log.info("literature.search.openalex.start queryExcerpt={} limit={} fetchLimit={} sortBy={} dateFrom={} dateTo={}",
                    LogSanitizer.safeExcerpt(query, 160), limit, fetchLimit, sortBy, dateFrom, dateTo);
            List<LiteratureSearchResult> items = openAlexLiteratureClient.search(
                    normalizedRequest(request, query, categories, dateFrom, dateTo, sortBy),
                    fetchLimit,
                    sortBy,
                    literatureProperties.openalex()
            );
            LiteratureSearchResponse response = new LiteratureSearchResponse(sortAndTrimToUserLimit(items, limit, sortBy));
            cacheIfEnabled(cacheKey, response);
            log.info("literature.search.done queryExcerpt={} limit={} fetchLimit={} sortBy={} dateFrom={} dateTo={} resultCount={} costMs={}",
                    LogSanitizer.safeExcerpt(query, 160), limit, fetchLimit, sortBy, dateFrom, dateTo, response.items().size(), elapsedMs(startNanos));
            return response;
        } catch (RuntimeException ex) {
            log.warn("literature.search.failed queryExcerpt={} limit={} sortBy={} dateFrom={} dateTo={} reason={} costMs={}",
                    LogSanitizer.safeExcerpt(query, 160), limit, sortBy, dateFrom, dateTo, exceptionCode(ex), elapsedMs(startNanos), ex);
            throw allSourcesUnavailable(ex);
        }
    }

    private LiteratureSearchRequest normalizedRequest(
            LiteratureSearchRequest request,
            String query,
            List<String> categories,
            String dateFrom,
            String dateTo,
            String sortBy
    ) {
        return new LiteratureSearchRequest(request.conversationId(), query, request.limit(), categories, dateFrom, dateTo, sortBy);
    }

    private int resolveFetchLimit(int limit, String sortBy) {
        if (!SORT_DATE.equals(sortBy)) {
            return limit;
        }
        return Math.min(Math.max(limit * 10, 10), 50);
    }

    private List<LiteratureSearchResult> sortAndTrimToUserLimit(
            List<LiteratureSearchResult> items,
            int limit,
            String sortBy
    ) {
        List<LiteratureSearchResult> sorted = SORT_DATE.equals(sortBy)
                ? items.stream()
                .sorted(Comparator.comparing(
                        LiteratureSearchResult::publishedDate,
                        Comparator.nullsLast(Comparator.reverseOrder())
                ))
                .toList()
                : items;
        if (sorted.size() <= limit) {
            return sorted;
        }
        return sorted.stream().limit(limit).toList();
    }

    private void cacheIfEnabled(LiteratureSearchCache.Key key, LiteratureSearchResponse response) {
        if (literatureProperties.cache().isEnabled()) {
            cache.put(key, response, cacheTtlWithJitter());
        }
    }

    private Duration cacheTtlWithJitter() {
        Duration ttl = literatureProperties.cache().ttl();
        Duration ttlJitter = literatureProperties.cache().ttlJitter();
        if (ttlJitter == null || ttlJitter.isZero() || ttlJitter.isNegative()) {
            return ttl;
        }
        long jitterMillis = ttlJitter.toMillis();
        if (jitterMillis <= 0) {
            return ttl;
        }
        long randomMillis = jitterMillis == Long.MAX_VALUE
                ? ThreadLocalRandom.current().nextLong(Long.MAX_VALUE)
                : ThreadLocalRandom.current().nextLong(jitterMillis + 1);
        return ttl.plusMillis(randomMillis);
    }

    private LiteratureSearchException allSourcesUnavailable(Throwable cause) {
        return new LiteratureSearchException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "LITERATURE_SEARCH_UNAVAILABLE",
                ALL_SOURCES_UNAVAILABLE,
                cause
        );
    }

    private int resolveLimit(Integer limit) {
        if (limit == null) {
            return 5;
        }
        if (limit > 50) {
            throw new LiteratureSearchException(HttpStatus.BAD_REQUEST, "LITERATURE_LIMIT_EXCEEDED", "limit 不能超过 50");
        }
        return limit;
    }

    private String resolveSortBy(String sortBy) {
        if (sortBy == null || sortBy.isBlank()) {
            return SORT_RELEVANCE;
        }
        String normalized = sortBy.trim().toLowerCase(Locale.ROOT);
        if (!SORT_RELEVANCE.equals(normalized) && !SORT_DATE.equals(normalized)) {
            throw new LiteratureSearchException(HttpStatus.BAD_REQUEST, "LITERATURE_SORT_BY_INVALID", "sortBy 只允许 relevance 或 date");
        }
        return normalized;
    }

    private List<String> normalizeCategories(List<String> categories) {
        if (categories == null || categories.isEmpty()) {
            return List.of();
        }
        return categories.stream()
                .filter(category -> category != null && !category.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }

    private String normalizeText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String normalizeQuery(String query) {
        String normalized = query.trim();
        if ("rag".equalsIgnoreCase(normalized)) {
            return "retrieval augmented generation";
        }
        return normalized;
    }

    private String exceptionCode(Throwable ex) {
        if (ex instanceof LiteratureSearchException literatureSearchException) {
            return literatureSearchException.code();
        }
        return ex == null ? "UNKNOWN" : ex.getClass().getSimpleName();
    }

    private long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
package com.lqr.paperragserver.literature.service;

import com.lqr.paperragserver.literature.client.OpenAlexLiteratureClient;
import com.lqr.paperragserver.literature.config.LiteratureSearchProperties;
import com.lqr.paperragserver.literature.exception.LiteratureSearchException;
import com.lqr.paperragserver.literature.model.LiteratureSearchRequest;
import com.lqr.paperragserver.literature.model.LiteratureSearchResponse;
import com.lqr.paperragserver.literature.model.LiteratureSearchResult;
import com.lqr.paperragserver.literature.support.LiteratureSearchCache;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 文献搜索业务入口。
 */
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
        int limit = resolveLimit(request.limit());
        String sortBy = resolveSortBy(request.sortBy());
        List<String> categories = normalizeCategories(request.categories());
        String query = normalizeQuery(request.query());
        String dateFrom = normalizeText(request.dateFrom());
        LiteratureSearchCache.Key cacheKey = new LiteratureSearchCache.Key(query, limit, sortBy, categories, dateFrom);

        if (literatureProperties.cache().isEnabled()) {
            var cached = cache.get(cacheKey);
            if (cached.isPresent()) {
                return cached.get();
            }
        }

        if (!literatureProperties.openalex().isEnabled()) {
            throw new LiteratureSearchException(HttpStatus.SERVICE_UNAVAILABLE, "OPENALEX_DISABLED", OPENALEX_DISABLED_MESSAGE);
        }

        try {
            int fetchLimit = resolveFetchLimit(limit, sortBy);
            List<LiteratureSearchResult> items = openAlexLiteratureClient.search(
                    normalizedRequest(request, query, categories, dateFrom, sortBy),
                    fetchLimit,
                    sortBy,
                    literatureProperties.openalex()
            );
            LiteratureSearchResponse response = new LiteratureSearchResponse(sortAndTrimToUserLimit(items, limit, sortBy));
            cacheIfEnabled(cacheKey, response);
            return response;
        } catch (RuntimeException ex) {
            throw allSourcesUnavailable(ex);
        }
    }

    private LiteratureSearchRequest normalizedRequest(
            LiteratureSearchRequest request,
            String query,
            List<String> categories,
            String dateFrom,
            String sortBy
    ) {
        return new LiteratureSearchRequest(request.conversationId(), query, request.limit(), categories, dateFrom, sortBy);
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
            cache.put(key, response, literatureProperties.cache().ttl());
        }
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
}
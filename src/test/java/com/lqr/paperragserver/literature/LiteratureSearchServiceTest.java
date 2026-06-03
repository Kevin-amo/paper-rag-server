package com.lqr.paperragserver.literature;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lqr.paperragserver.literature.client.OpenAlexLiteratureClient;
import com.lqr.paperragserver.literature.config.LiteratureSearchProperties;
import com.lqr.paperragserver.literature.exception.LiteratureSearchException;
import com.lqr.paperragserver.literature.model.LiteratureSearchRequest;
import com.lqr.paperragserver.literature.model.LiteratureSearchResponse;
import com.lqr.paperragserver.literature.model.LiteratureSearchResult;
import com.lqr.paperragserver.literature.service.LiteratureSearchService;
import com.lqr.paperragserver.literature.support.LiteratureSearchCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class LiteratureSearchServiceTest {

    private final OpenAlexLiteratureClient openAlexLiteratureClient = mock(OpenAlexLiteratureClient.class);
    private LiteratureSearchService service;

    @BeforeEach
    void setUp() {
        service = new LiteratureSearchService(searchProperties(true, true), openAlexLiteratureClient, testCache());
    }

    @Test
    void searchShouldReturnOpenAlexResults() {
        LiteratureSearchResult result = openAlexResult("Graph RAG");
        when(openAlexLiteratureClient.search(any(), anyInt(), anyString(), any())).thenReturn(List.of(result));

        LiteratureSearchResponse response = service.search(new LiteratureSearchRequest("Graph RAG", 7, null, null, "relevance"));

        assertThat(response.items()).containsExactly(result);
        verify(openAlexLiteratureClient).search(any(LiteratureSearchRequest.class), org.mockito.ArgumentMatchers.eq(7), org.mockito.ArgumentMatchers.eq("relevance"), any());
    }

    @Test
    void searchShouldCacheOpenAlexResults() {
        LiteratureSearchResult result = openAlexResult("Graph RAG");
        when(openAlexLiteratureClient.search(any(), anyInt(), anyString(), any())).thenReturn(List.of(result));

        LiteratureSearchRequest request = new LiteratureSearchRequest("Graph RAG", 10, List.of("cs.AI"), null, "relevance");
        LiteratureSearchResponse first = service.search(request);
        LiteratureSearchResponse second = service.search(request);

        assertThat(first.items()).containsExactly(result);
        assertThat(second.items()).containsExactly(result);
        verify(openAlexLiteratureClient, times(1)).search(any(), anyInt(), anyString(), any());
    }

    @Test
    void searchShouldExpandAmbiguousRagAbbreviation() {
        LiteratureSearchResult result = openAlexResult("Retrieval-Augmented Generation");
        when(openAlexLiteratureClient.search(any(), anyInt(), anyString(), any())).thenReturn(List.of(result));

        LiteratureSearchResponse response = service.search(new LiteratureSearchRequest("RAG", 3, null, null, "relevance"));

        assertThat(response.items()).containsExactly(result);
        verify(openAlexLiteratureClient).search(
                org.mockito.ArgumentMatchers.argThat(request -> "retrieval augmented generation".equals(request.query())),
                org.mockito.ArgumentMatchers.eq(3),
                org.mockito.ArgumentMatchers.eq("relevance"),
                any()
        );
    }

    @Test
    void searchShouldUseFiveAsDefaultLimitWhenUserDoesNotSpecifyCount() {
        LiteratureSearchResult result = openAlexResult("Graph RAG");
        when(openAlexLiteratureClient.search(any(), anyInt(), anyString(), any())).thenReturn(List.of(result));

        LiteratureSearchResponse response = service.search(new LiteratureSearchRequest("Graph RAG", null, null, null, "relevance"));

        assertThat(response.items()).containsExactly(result);
        verify(openAlexLiteratureClient).search(
                any(LiteratureSearchRequest.class),
                org.mockito.ArgumentMatchers.eq(5),
                org.mockito.ArgumentMatchers.eq("relevance"),
                any()
        );
    }

    @Test
    void searchShouldReturnEmptyWhenOpenAlexReturnsEmpty() {
        when(openAlexLiteratureClient.search(any(), anyInt(), anyString(), any())).thenReturn(List.of());

        LiteratureSearchResponse response = service.search(new LiteratureSearchRequest("No Result", null, null, null, null));

        assertThat(response.items()).isEmpty();
        verify(openAlexLiteratureClient).search(any(), anyInt(), anyString(), any());
    }

    @Test
    void searchShouldWrapOpenAlexFailureAsUnavailableError() {
        when(openAlexLiteratureClient.search(any(), anyInt(), anyString(), any()))
                .thenThrow(new LiteratureSearchException(HttpStatus.BAD_GATEWAY, "OPENALEX_FAILED", "OpenAlex 文献服务调用失败"));

        assertThatThrownBy(() -> service.search(new LiteratureSearchRequest("Graph RAG", null, null, null, null)))
                .isInstanceOf(LiteratureSearchException.class)
                .satisfies(error -> {
                    LiteratureSearchException ex = (LiteratureSearchException) error;
                    assertThat(ex.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(ex.code()).isEqualTo("LITERATURE_SEARCH_UNAVAILABLE");
                    assertThat(ex.getMessage()).isEqualTo("外部文献服务暂不可用，请稍后重试");
                });
    }

    @Test
    void searchShouldRejectLimitOverMax() {
        assertThatThrownBy(() -> service.search(new LiteratureSearchRequest("Graph RAG", 51, null, null, null)))
                .isInstanceOf(LiteratureSearchException.class)
                .satisfies(error -> {
                    LiteratureSearchException ex = (LiteratureSearchException) error;
                    assertThat(ex.status()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.code()).isEqualTo("LITERATURE_LIMIT_EXCEEDED");
                });
        verifyNoInteractions(openAlexLiteratureClient);
    }

    @Test
    void searchShouldRejectUnsupportedSortBy() {
        assertThatThrownBy(() -> service.search(new LiteratureSearchRequest("Graph RAG", 10, null, null, "score")))
                .isInstanceOf(LiteratureSearchException.class)
                .satisfies(error -> {
                    LiteratureSearchException ex = (LiteratureSearchException) error;
                    assertThat(ex.status()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.code()).isEqualTo("LITERATURE_SORT_BY_INVALID");
                });
        verifyNoInteractions(openAlexLiteratureClient);
    }

    @Test
    void searchShouldFailClearlyWhenOpenAlexDisabled() {
        LiteratureSearchService disabledService = new LiteratureSearchService(searchProperties(false, true), openAlexLiteratureClient, testCache());

        assertThatThrownBy(() -> disabledService.search(new LiteratureSearchRequest("Graph RAG", null, null, null, null)))
                .isInstanceOf(LiteratureSearchException.class)
                .satisfies(error -> {
                    LiteratureSearchException ex = (LiteratureSearchException) error;
                    assertThat(ex.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(ex.code()).isEqualTo("OPENALEX_DISABLED");
                });
        verifyNoInteractions(openAlexLiteratureClient);
    }

    @Test
    void dateSearchShouldFetchMoreCandidatesAndReturnOnlyUserLimit() {
        LiteratureSearchResult newest = openAlexResult("Newest", "2025-01-01");
        LiteratureSearchResult older = openAlexResult("Older", "2024-01-01");
        when(openAlexLiteratureClient.search(any(), anyInt(), anyString(), any())).thenReturn(List.of(older, newest));

        LiteratureSearchResponse response = service.search(new LiteratureSearchRequest("Graph RAG", 1, null, null, "date"));

        assertThat(response.items()).containsExactly(newest);
        verify(openAlexLiteratureClient).search(
                any(LiteratureSearchRequest.class),
                org.mockito.ArgumentMatchers.eq(10),
                org.mockito.ArgumentMatchers.eq("date"),
                any()
        );
    }

    @Test
    void cacheShouldSeparateDateSortFromRelevanceSortByUserLimit() {
        LiteratureSearchResult relevanceResult = openAlexResult("Relevant", "2023-01-01");
        LiteratureSearchResult dateResult = openAlexResult("Recent", "2025-01-01");
        when(openAlexLiteratureClient.search(any(), anyInt(), org.mockito.ArgumentMatchers.eq("relevance"), any()))
                .thenReturn(List.of(relevanceResult));
        when(openAlexLiteratureClient.search(any(), anyInt(), org.mockito.ArgumentMatchers.eq("date"), any()))
                .thenReturn(List.of(dateResult));

        LiteratureSearchResponse relevance = service.search(new LiteratureSearchRequest("Graph RAG", 1, null, null, "relevance"));
        LiteratureSearchResponse date = service.search(new LiteratureSearchRequest("Graph RAG", 1, null, null, "date"));
        LiteratureSearchResponse cachedDate = service.search(new LiteratureSearchRequest("Graph RAG", 1, null, null, "date"));

        assertThat(relevance.items()).containsExactly(relevanceResult);
        assertThat(date.items()).containsExactly(dateResult);
        assertThat(cachedDate.items()).containsExactly(dateResult);
        verify(openAlexLiteratureClient, times(1)).search(any(), org.mockito.ArgumentMatchers.eq(1), org.mockito.ArgumentMatchers.eq("relevance"), any());
        verify(openAlexLiteratureClient, times(1)).search(any(), org.mockito.ArgumentMatchers.eq(10), org.mockito.ArgumentMatchers.eq("date"), any());
    }

    @Test
    void searchShouldPassDateToToOpenAlexClient() {
        LiteratureSearchResult result = openAlexResult("RAG 2026", "2026-01-01");
        when(openAlexLiteratureClient.search(any(), anyInt(), anyString(), any())).thenReturn(List.of(result));

        LiteratureSearchResponse response = service.search(new LiteratureSearchRequest("Graph RAG", 3, null, "2026-01-01", "2026-12-31", "date"));

        assertThat(response.items()).containsExactly(result);
        verify(openAlexLiteratureClient).search(
                org.mockito.ArgumentMatchers.argThat(request -> "2026-12-31".equals(request.dateTo())),
                org.mockito.ArgumentMatchers.eq(30),
                org.mockito.ArgumentMatchers.eq("date"),
                any()
        );
    }

    @Test
    void cacheShouldSeparateDateTo() {
        LiteratureSearchResult firstRange = openAlexResult("First Range", "2026-01-01");
        LiteratureSearchResult secondRange = openAlexResult("Second Range", "2026-06-01");
        when(openAlexLiteratureClient.search(org.mockito.ArgumentMatchers.argThat(request -> request != null && "2026-03-31".equals(request.dateTo())), anyInt(), anyString(), any()))
                .thenReturn(List.of(firstRange));
        when(openAlexLiteratureClient.search(org.mockito.ArgumentMatchers.argThat(request -> request != null && "2026-12-31".equals(request.dateTo())), anyInt(), anyString(), any()))
                .thenReturn(List.of(secondRange));

        LiteratureSearchResponse first = service.search(new LiteratureSearchRequest("Graph RAG", 1, null, "2026-01-01", "2026-03-31", "relevance"));
        LiteratureSearchResponse second = service.search(new LiteratureSearchRequest("Graph RAG", 1, null, "2026-01-01", "2026-12-31", "relevance"));
        LiteratureSearchResponse cachedFirst = service.search(new LiteratureSearchRequest("Graph RAG", 1, null, "2026-01-01", "2026-03-31", "relevance"));

        assertThat(first.items()).containsExactly(firstRange);
        assertThat(second.items()).containsExactly(secondRange);
        assertThat(cachedFirst.items()).containsExactly(firstRange);
        verify(openAlexLiteratureClient, times(1)).search(org.mockito.ArgumentMatchers.argThat(request -> request != null && "2026-03-31".equals(request.dateTo())), anyInt(), anyString(), any());
        verify(openAlexLiteratureClient, times(1)).search(org.mockito.ArgumentMatchers.argThat(request -> request != null && "2026-12-31".equals(request.dateTo())), anyInt(), anyString(), any());
    }

    @Test
    void cacheMissWithLockShouldCheckCacheAgain() {
        LiteratureSearchCache guardedCache = mock(LiteratureSearchCache.class);
        LiteratureSearchCache.LockHandle lockHandle = new LiteratureSearchCache.LockHandle("lock-key", "token");
        LiteratureSearchResult result = openAlexResult("Graph RAG");
        when(guardedCache.get(any())).thenReturn(Optional.empty());
        when(guardedCache.tryAcquireLock(any(), any())).thenReturn(Optional.of(lockHandle));
        when(openAlexLiteratureClient.search(any(), anyInt(), anyString(), any())).thenReturn(List.of(result));
        LiteratureSearchService guardedService = new LiteratureSearchService(searchProperties(true, true), openAlexLiteratureClient, guardedCache);

        LiteratureSearchResponse response = guardedService.search(new LiteratureSearchRequest("Graph RAG", 10, null, null, "relevance"));

        assertThat(response.items()).containsExactly(result);
        verify(guardedCache, times(2)).get(any());
        verify(guardedCache).releaseLock(lockHandle);
    }

    @Test
    void cacheMissWithLockShouldReturnSecondCacheHitWithoutCallingOpenAlex() {
        LiteratureSearchCache guardedCache = mock(LiteratureSearchCache.class);
        LiteratureSearchCache.LockHandle lockHandle = new LiteratureSearchCache.LockHandle("lock-key", "token");
        LiteratureSearchResponse cachedResponse = new LiteratureSearchResponse(List.of(openAlexResult("Cached Graph RAG")));
        when(guardedCache.get(any())).thenReturn(Optional.empty(), Optional.of(cachedResponse));
        when(guardedCache.tryAcquireLock(any(), any())).thenReturn(Optional.of(lockHandle));
        LiteratureSearchService guardedService = new LiteratureSearchService(searchProperties(true, true), openAlexLiteratureClient, guardedCache);

        LiteratureSearchResponse response = guardedService.search(new LiteratureSearchRequest("Graph RAG", 10, null, null, "relevance"));

        assertThat(response).isEqualTo(cachedResponse);
        verifyNoInteractions(openAlexLiteratureClient);
        verify(guardedCache).releaseLock(lockHandle);
        verify(guardedCache, never()).put(any(), any(), any());
    }

    @Test
    void cacheMissWithLockAndSecondMissShouldCallOpenAlexOnceAndWriteCache() {
        LiteratureSearchCache guardedCache = mock(LiteratureSearchCache.class);
        LiteratureSearchCache.LockHandle lockHandle = new LiteratureSearchCache.LockHandle("lock-key", "token");
        LiteratureSearchResult result = openAlexResult("Graph RAG");
        when(guardedCache.get(any())).thenReturn(Optional.empty());
        when(guardedCache.tryAcquireLock(any(), any())).thenReturn(Optional.of(lockHandle));
        when(openAlexLiteratureClient.search(any(), anyInt(), anyString(), any())).thenReturn(List.of(result));
        LiteratureSearchService guardedService = new LiteratureSearchService(searchProperties(true, true), openAlexLiteratureClient, guardedCache);

        LiteratureSearchResponse response = guardedService.search(new LiteratureSearchRequest("Graph RAG", 10, null, null, "relevance"));

        assertThat(response.items()).containsExactly(result);
        verify(openAlexLiteratureClient, times(1)).search(any(), anyInt(), anyString(), any());
        verify(guardedCache).put(any(), org.mockito.ArgumentMatchers.eq(response), org.mockito.ArgumentMatchers.eq(Duration.ofMinutes(20)));
        verify(guardedCache).releaseLock(lockHandle);
    }

    @Test
    void cacheMissWithoutLockShouldReturnWaitedCacheHitWithoutCallingOpenAlex() {
        LiteratureSearchCache guardedCache = mock(LiteratureSearchCache.class);
        LiteratureSearchResponse cachedResponse = new LiteratureSearchResponse(List.of(openAlexResult("Cached Graph RAG")));
        when(guardedCache.get(any())).thenReturn(Optional.empty(), Optional.of(cachedResponse));
        when(guardedCache.tryAcquireLock(any(), any())).thenReturn(Optional.empty());
        LiteratureSearchService guardedService = new LiteratureSearchService(searchProperties(true, true), openAlexLiteratureClient, guardedCache);

        LiteratureSearchResponse response = guardedService.search(new LiteratureSearchRequest("Graph RAG", 10, null, null, "relevance"));

        assertThat(response).isEqualTo(cachedResponse);
        verifyNoInteractions(openAlexLiteratureClient);
        verify(guardedCache, times(2)).get(any());
    }

    @Test
    void cacheMissWithoutLockAndWaitMissShouldFallbackToOpenAlexOnce() {
        LiteratureSearchCache guardedCache = mock(LiteratureSearchCache.class);
        LiteratureSearchResult result = openAlexResult("Graph RAG");
        when(guardedCache.get(any())).thenReturn(Optional.empty());
        when(guardedCache.tryAcquireLock(any(), any())).thenReturn(Optional.empty());
        when(openAlexLiteratureClient.search(any(), anyInt(), anyString(), any())).thenReturn(List.of(result));
        LiteratureSearchService guardedService = new LiteratureSearchService(searchProperties(true, true), openAlexLiteratureClient, guardedCache);

        LiteratureSearchResponse response = guardedService.search(new LiteratureSearchRequest("Graph RAG", 10, null, null, "relevance"));

        assertThat(response.items()).containsExactly(result);
        verify(openAlexLiteratureClient, times(1)).search(any(), anyInt(), anyString(), any());
        verify(guardedCache).put(any(), org.mockito.ArgumentMatchers.eq(response), org.mockito.ArgumentMatchers.eq(Duration.ofMinutes(20)));
    }

    private LiteratureSearchProperties searchProperties(boolean openAlexEnabled, boolean cacheEnabled) {
        return new LiteratureSearchProperties(
                new LiteratureSearchProperties.OpenAlex(openAlexEnabled, "https://api.openalex.org/works", Duration.ofSeconds(10), null),
                new LiteratureSearchProperties.Cache(cacheEnabled, Duration.ofMinutes(20), Duration.ofSeconds(10), Duration.ofMillis(1), 1, Duration.ZERO)
        );
    }

    private LiteratureSearchCache testCache() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        Map<String, String> store = new HashMap<>();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenAnswer(invocation -> store.get(invocation.getArgument(0)));
        doAnswer(invocation -> {
            store.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(valueOperations).set(anyString(), anyString(), any(Duration.class));
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            if (store.containsKey(key)) {
                return false;
            }
            store.put(key, invocation.getArgument(1));
            return true;
        });
        doAnswer(invocation -> store.remove(invocation.getArgument(0)) != null).when(redisTemplate).delete(anyString());
        return new LiteratureSearchCache(redisTemplate, new ObjectMapper());
    }

    private LiteratureSearchResult openAlexResult(String title) {
        return openAlexResult(title, "2024-01-01");
    }

    private LiteratureSearchResult openAlexResult(String title, String publishedDate) {
        return new LiteratureSearchResult(
                title, List.of("Alice"), "Abstract", 2024, publishedDate, null,
                List.of("Artificial Intelligence"), "Artificial Intelligence", "https://doi.org/10.1000/test",
                "https://example.org/paper", "https://example.org/paper.pdf", "openalex", "https://openalex.org/W123"
        );
    }
}
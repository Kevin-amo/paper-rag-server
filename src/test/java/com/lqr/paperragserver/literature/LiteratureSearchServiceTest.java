package com.lqr.paperragserver.literature;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class LiteratureSearchServiceTest {

    private final OpenAlexLiteratureClient openAlexLiteratureClient = mock(OpenAlexLiteratureClient.class);
    private LiteratureSearchService service;

    @BeforeEach
    void setUp() {
        service = new LiteratureSearchService(searchProperties(true, true), openAlexLiteratureClient, new LiteratureSearchCache());
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
        LiteratureSearchService disabledService = new LiteratureSearchService(searchProperties(false, true), openAlexLiteratureClient, new LiteratureSearchCache());

        assertThatThrownBy(() -> disabledService.search(new LiteratureSearchRequest("Graph RAG", null, null, null, null)))
                .isInstanceOf(LiteratureSearchException.class)
                .satisfies(error -> {
                    LiteratureSearchException ex = (LiteratureSearchException) error;
                    assertThat(ex.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(ex.code()).isEqualTo("OPENALEX_DISABLED");
                });
        verifyNoInteractions(openAlexLiteratureClient);
    }

    private LiteratureSearchProperties searchProperties(boolean openAlexEnabled, boolean cacheEnabled) {
        return new LiteratureSearchProperties(
                new LiteratureSearchProperties.OpenAlex(openAlexEnabled, "https://api.openalex.org/works", Duration.ofSeconds(10), null),
                new LiteratureSearchProperties.Cache(cacheEnabled, Duration.ofMinutes(20))
        );
    }

    private LiteratureSearchResult openAlexResult(String title) {
        return new LiteratureSearchResult(
                title, List.of("Alice"), "Abstract", 2024, "2024-01-01", null,
                List.of("Artificial Intelligence"), "Artificial Intelligence", "https://doi.org/10.1000/test", "https://example.org/paper",
                "https://example.org/paper.pdf", "openalex", "https://openalex.org/W123"
        );
    }
}
package com.lqr.paperragserver.literature.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lqr.paperragserver.literature.config.LiteratureSearchProperties;
import com.lqr.paperragserver.literature.model.LiteratureSearchRequest;
import com.lqr.paperragserver.literature.model.LiteratureSearchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class OpenAlexLiteratureClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private OpenAlexLiteratureClient client;

    @BeforeEach
    void setUp() {
        client = new OpenAlexLiteratureClient(mock(RestClient.Builder.class), objectMapper);
    }

    @Test
    void normalizeShouldMapOpenAlexWorkToLiteratureResult() throws Exception {
        var raw = objectMapper.readTree("""
                {
                  "results": [
                    {
                      "id": "https://openalex.org/W123",
                      "doi": "https://doi.org/10.1000/test",
                      "title": "Graph RAG with OpenAlex",
                      "publication_year": 2024,
                      "publication_date": "2024-02-03",
                      "abstract_inverted_index": {
                        "Graph": [0],
                        "RAG": [1],
                        "works": [2]
                      },
                      "authorships": [
                        {"author": {"display_name": "Alice"}},
                        {"author": {"display_name": "Bob"}}
                      ],
                      "primary_topic": {"display_name": "Artificial Intelligence"},
                      "concepts": [
                        {"display_name": "Artificial Intelligence"},
                        {"display_name": "Information Retrieval"}
                      ],
                      "primary_location": {
                        "landing_page_url": "https://example.org/paper",
                        "pdf_url": "https://example.org/fallback.pdf"
                      },
                      "open_access": {
                        "oa_url": "https://example.org/open.pdf"
                      }
                    }
                  ]
                }
                """);

        var results = client.normalize(raw);

        assertThat(results).hasSize(1);
        LiteratureSearchResult result = results.get(0);
        assertThat(result.title()).isEqualTo("Graph RAG with OpenAlex");
        assertThat(result.authors()).containsExactly("Alice", "Bob");
        assertThat(result.abstractText()).isEqualTo("Graph RAG works");
        assertThat(result.year()).isEqualTo(2024);
        assertThat(result.publishedDate()).isEqualTo("2024-02-03");
        assertThat(result.categories()).containsExactly("Artificial Intelligence", "Information Retrieval");
        assertThat(result.primaryCategory()).isEqualTo("Artificial Intelligence");
        assertThat(result.doi()).isEqualTo("https://doi.org/10.1000/test");
        assertThat(result.url()).isEqualTo("https://example.org/paper");
        assertThat(result.pdfUrl()).isEqualTo("https://example.org/open.pdf");
        assertThat(result.source()).isEqualTo("openalex");
        assertThat(result.externalId()).isEqualTo("https://openalex.org/W123");
    }

    @Test
    void restoreAbstractShouldOrderWordsByPosition() throws Exception {
        var invertedIndex = objectMapper.readTree("""
                {
                  "paper": [3],
                  "A": [0],
                  "useful": [1],
                  "RAG": [2]
                }
                """);

        assertThat(client.restoreAbstract(invertedIndex)).isEqualTo("A useful RAG paper");
    }

    @Test
    void openAlexUriShouldEncodeQueryWithSpacesAndChineseCharacters() {
        var properties = new LiteratureSearchProperties.OpenAlex(
                true,
                "https://api.openalex.org/works",
                Duration.ofSeconds(10),
                null
        );

        var uri = client.openAlexUri(
                new LiteratureSearchRequest("learning machine 中文", 10, null, null, "relevance"),
                10,
                "relevance",
                properties
        );

        assertThat(uri.toString()).contains("search=learning%20machine%20%E4%B8%AD%E6%96%87");
    }

    @Test
    void openAlexUriShouldUsePublicationDateSortWhenDateSortRequested() {
        var properties = new LiteratureSearchProperties.OpenAlex(
                true,
                "https://api.openalex.org/works",
                Duration.ofSeconds(10),
                null
        );

        var uri = client.openAlexUri(
                new LiteratureSearchRequest("RAG", 3, null, null, "date"),
                3,
                "date",
                properties
        );

        assertThat(uri.toString()).contains("sort=publication_date:desc");
    }

    @Test
    void openAlexUriShouldUseRelevanceScoreSortWhenRelevanceSortRequested() {
        var properties = new LiteratureSearchProperties.OpenAlex(
                true,
                "https://api.openalex.org/works",
                Duration.ofSeconds(10),
                null
        );

        var uri = client.openAlexUri(
                new LiteratureSearchRequest("RAG", 3, null, null, "relevance"),
                3,
                "relevance",
                properties
        );

        assertThat(uri.toString()).contains("sort=relevance_score:desc");
    }

    @Test
    void dateSortShouldOrderNormalizedResultsFromNewestToOldestWithMissingDateLast() throws Exception {
        var raw = objectMapper.readTree("""
                {
                  "results": [
                    {"id": "old", "title": "Old", "publication_date": "2023-01-01"},
                    {"id": "missing", "title": "Missing"},
                    {"id": "new", "title": "New", "publication_date": "2025-01-01"}
                  ]
                }
                """);

        List<LiteratureSearchResult> results = client.sortByDateDescending(client.normalize(raw));

        assertThat(results)
                .extracting(LiteratureSearchResult::title)
                .containsExactly("New", "Old", "Missing");
    }

    @Test
    void normalizeShouldReturnEmptyWhenResultsMissingOrEmpty() throws Exception {
        assertThat(client.normalize(objectMapper.readTree("{}"))).isEmpty();
        assertThat(client.normalize(objectMapper.readTree("{\"results\":[]}"))).isEmpty();
    }
}
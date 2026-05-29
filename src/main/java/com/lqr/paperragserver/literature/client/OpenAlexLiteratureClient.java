package com.lqr.paperragserver.literature.client;

import com.lqr.paperragserver.common.logging.LogSanitizer;
import com.lqr.paperragserver.literature.config.LiteratureSearchProperties;
import com.lqr.paperragserver.literature.exception.LiteratureSearchException;
import com.lqr.paperragserver.literature.model.LiteratureSearchRequest;
import com.lqr.paperragserver.literature.model.LiteratureSearchResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.SocketTimeoutException;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * OpenAlex 文献搜索客户端。
 */
@Slf4j
@Service
public class OpenAlexLiteratureClient {

    private static final String SORT_DATE = "date";

    private final RestClient.Builder restClientBuilder;
    private final ObjectMapper objectMapper;

    public OpenAlexLiteratureClient(RestClient.Builder restClientBuilder, ObjectMapper objectMapper) {
        this.restClientBuilder = restClientBuilder;
        this.objectMapper = objectMapper;
    }

    public List<LiteratureSearchResult> search(LiteratureSearchRequest request, int limit, String sortBy, LiteratureSearchProperties.OpenAlex properties) {
        if (!properties.isEnabled()) {
            log.warn("literature.search.openalex.skipped queryExcerpt={} limit={} sortBy={} dateFrom={} reason=DISABLED",
                    LogSanitizer.safeExcerpt(request.query(), 160), limit, sortBy, request.dateFrom());
            return List.of();
        }
        long startNanos = System.nanoTime();
        URI uri = openAlexUri(request, limit, sortBy, properties);
        log.info("literature.search.openalex.request queryExcerpt={} limit={} sortBy={} dateFrom={} urlSummary={}",
                LogSanitizer.safeExcerpt(request.query(), 160), limit, sortBy, request.dateFrom(), LogSanitizer.safeUriSummary(uri));
        try {
            JsonNode raw = client(properties.timeout())
                    .get()
                    .uri(uri)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(JsonNode.class);
            int rawResultCount = rawResultCount(raw);
            List<LiteratureSearchResult> results = normalize(raw);
            int parseFailedCount = Math.max(0, rawResultCount - results.size());
            List<LiteratureSearchResult> finalResults = SORT_DATE.equals(sortBy) ? sortByDateDescending(results) : results;
            log.info("literature.search.openalex.done queryExcerpt={} limit={} sortBy={} dateFrom={} rawResultCount={} parsedCount={} parseFailedCount={} costMs={}",
                    LogSanitizer.safeExcerpt(request.query(), 160), limit, sortBy, request.dateFrom(), rawResultCount, finalResults.size(), parseFailedCount, elapsedMs(startNanos));
            return finalResults;
        } catch (ResourceAccessException ex) {
            if (isTimeout(ex)) {
                log.warn("literature.search.openalex.failed queryExcerpt={} limit={} sortBy={} dateFrom={} reason=OPENALEX_TIMEOUT costMs={}",
                        LogSanitizer.safeExcerpt(request.query(), 160), limit, sortBy, request.dateFrom(), elapsedMs(startNanos), ex);
                throw new LiteratureSearchException(HttpStatus.GATEWAY_TIMEOUT, "OPENALEX_TIMEOUT", "OpenAlex 调用超时", ex);
            }
            log.warn("literature.search.openalex.failed queryExcerpt={} limit={} sortBy={} dateFrom={} reason=OPENALEX_CONNECTION_FAILED costMs={}",
                    LogSanitizer.safeExcerpt(request.query(), 160), limit, sortBy, request.dateFrom(), elapsedMs(startNanos), ex);
            throw new LiteratureSearchException(HttpStatus.BAD_GATEWAY, "OPENALEX_CONNECTION_FAILED", "无法连接 OpenAlex 文献服务", ex);
        } catch (RestClientException ex) {
            if (isTimeout(ex)) {
                log.warn("literature.search.openalex.failed queryExcerpt={} limit={} sortBy={} dateFrom={} reason=OPENALEX_TIMEOUT costMs={}",
                        LogSanitizer.safeExcerpt(request.query(), 160), limit, sortBy, request.dateFrom(), elapsedMs(startNanos), ex);
                throw new LiteratureSearchException(HttpStatus.GATEWAY_TIMEOUT, "OPENALEX_TIMEOUT", "OpenAlex 调用超时", ex);
            }
            log.warn("literature.search.openalex.failed queryExcerpt={} limit={} sortBy={} dateFrom={} reason=OPENALEX_FAILED costMs={}",
                    LogSanitizer.safeExcerpt(request.query(), 160), limit, sortBy, request.dateFrom(), elapsedMs(startNanos), ex);
            throw new LiteratureSearchException(HttpStatus.BAD_GATEWAY, "OPENALEX_FAILED", "OpenAlex 文献服务调用失败", ex);
        } catch (RuntimeException ex) {
            if (ex instanceof LiteratureSearchException literatureSearchException) {
                throw literatureSearchException;
            }
            log.warn("literature.search.openalex.parse.failed queryExcerpt={} limit={} sortBy={} dateFrom={} reason=OPENALEX_RESPONSE_INVALID costMs={}",
                    LogSanitizer.safeExcerpt(request.query(), 160), limit, sortBy, request.dateFrom(), elapsedMs(startNanos), ex);
            throw new LiteratureSearchException(HttpStatus.BAD_GATEWAY, "OPENALEX_RESPONSE_INVALID", "OpenAlex 文献服务响应无法解析", ex);
        }
    }

    List<LiteratureSearchResult> normalize(JsonNode raw) {
        if (raw == null || raw.isNull() || raw.isMissingNode()) {
            return List.of();
        }
        JsonNode results = raw.path("results");
        if (!results.isArray() || results.isEmpty()) {
            return List.of();
        }
        List<LiteratureSearchResult> items = new ArrayList<>();
        for (JsonNode item : results) {
            if (item.isObject()) {
                items.add(toResult(item));
            }
        }
        return items;
    }

    List<LiteratureSearchResult> sortByDateDescending(List<LiteratureSearchResult> results) {
        return results.stream()
                .sorted(Comparator.comparing(
                        LiteratureSearchResult::publishedDate,
                        Comparator.nullsLast(Comparator.reverseOrder())
                ))
                .toList();
    }

    String restoreAbstract(JsonNode invertedIndex) {
        if (invertedIndex == null || invertedIndex.isNull() || invertedIndex.isMissingNode() || !invertedIndex.isObject()) {
            return null;
        }
        List<PositionedWord> words = new ArrayList<>();
        invertedIndex.fields().forEachRemaining(entry -> {
            JsonNode positions = entry.getValue();
            if (positions.isArray()) {
                for (JsonNode position : positions) {
                    if (position.canConvertToInt()) {
                        words.add(new PositionedWord(position.asInt(), entry.getKey()));
                    }
                }
            }
        });
        if (words.isEmpty()) {
            return null;
        }
        words.sort(Comparator.comparingInt(PositionedWord::position));
        String text = words.stream()
                .map(PositionedWord::word)
                .reduce((left, right) -> left + " " + right)
                .orElse("")
                .trim();
        return text.isBlank() ? null : text;
    }

    private LiteratureSearchResult toResult(JsonNode node) {
        List<String> categories = categories(node);
        String primaryCategory = firstText(node.path("primary_topic"), List.of("display_name"));
        if ((primaryCategory == null || primaryCategory.isBlank()) && !categories.isEmpty()) {
            primaryCategory = categories.get(0);
        }
        String doi = firstText(node, List.of("doi"));
        return new LiteratureSearchResult(
                firstText(node, List.of("title", "display_name")),
                authors(node.path("authorships")),
                restoreAbstract(node.path("abstract_inverted_index")),
                intValue(node.path("publication_year")),
                firstText(node, List.of("publication_date")),
                null,
                categories,
                primaryCategory,
                doi,
                firstNonBlank(
                        firstText(node.path("primary_location"), List.of("landing_page_url")),
                        doi
                ),
                firstNonBlank(
                        firstText(node.path("open_access"), List.of("oa_url")),
                        firstText(node.path("primary_location"), List.of("pdf_url"))
                ),
                "openalex",
                firstText(node, List.of("id"))
        );
    }

    private List<String> authors(JsonNode authorships) {
        if (!authorships.isArray()) {
            return List.of();
        }
        Set<String> items = new LinkedHashSet<>();
        for (JsonNode authorship : authorships) {
            addNonBlank(items, firstText(authorship.path("author"), List.of("display_name")));
        }
        return List.copyOf(items);
    }

    private List<String> categories(JsonNode node) {
        Set<String> items = new LinkedHashSet<>();
        addNonBlank(items, firstText(node.path("primary_topic"), List.of("display_name")));
        JsonNode concepts = node.path("concepts");
        if (concepts.isArray()) {
            for (JsonNode concept : concepts) {
                addNonBlank(items, firstText(concept, List.of("display_name")));
            }
        }
        return List.copyOf(items);
    }

    private void addNonBlank(Set<String> items, String value) {
        if (value != null && !value.isBlank()) {
            items.add(value.trim());
        }
    }

    private String firstText(JsonNode node, List<String> fields) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        for (String field : fields) {
            JsonNode value = node.path(field);
            if (value.isTextual()) {
                String text = value.asText().trim();
                if (!text.isBlank()) {
                    return text;
                }
            } else if (value.isNumber() || value.isBoolean()) {
                return value.asText();
            }
        }
        return null;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private Integer intValue(JsonNode value) {
        if (value == null || value.isNull() || value.isMissingNode()) {
            return null;
        }
        if (value.canConvertToInt()) {
            return value.asInt();
        }
        if (value.isTextual()) {
            try {
                return Integer.parseInt(value.asText().trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String openAlexSort(String sortBy) {
        return "relevance_score:desc";
    }

    URI openAlexUri(
            LiteratureSearchRequest request,
            int limit,
            String sortBy,
            LiteratureSearchProperties.OpenAlex properties
    ) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(properties.endpoint())
                .queryParam("search", request.query().trim())
                .queryParam("per-page", limit)
                .queryParam("sort", openAlexSort(sortBy));
        if (properties.mailto() != null && !properties.mailto().isBlank()) {
            builder.queryParam("mailto", properties.mailto());
        }
        if (request.dateFrom() != null && !request.dateFrom().isBlank()) {
            builder.queryParam("filter", "from_publication_date:" + request.dateFrom().trim());
        }
        return builder.build().encode().toUri();
    }

    private RestClient client(Duration timeout) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);
        return restClientBuilder
                .requestFactory(requestFactory)
                .messageConverters(converters -> converters.add(0, new org.springframework.http.converter.json.MappingJackson2HttpMessageConverter(objectMapper)))
                .build();
    }

    private int rawResultCount(JsonNode raw) {
        if (raw == null || raw.isNull() || raw.isMissingNode()) {
            return 0;
        }
        JsonNode results = raw.path("results");
        return results.isArray() ? results.size() : 0;
    }

    private long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    private boolean isTimeout(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof SocketTimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private record PositionedWord(int position, String word) {
    }
}
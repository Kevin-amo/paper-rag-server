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

    /**
     * 创建 OpenAlex 客户端，复用 Spring HTTP 客户端构建器和 JSON 映射器。
     */
    public OpenAlexLiteratureClient(RestClient.Builder restClientBuilder, ObjectMapper objectMapper) {
        this.restClientBuilder = restClientBuilder;
        this.objectMapper = objectMapper;
    }

    /**
     * 调用 OpenAlex 搜索接口，并将响应转换为统一文献搜索结果。
     */
    public List<LiteratureSearchResult> search(LiteratureSearchRequest request, int limit, String sortBy, LiteratureSearchProperties.OpenAlex properties) {
        if (!properties.isEnabled()) {
            log.warn("literature.search.openalex.skipped queryExcerpt={} limit={} sortBy={} dateFrom={} dateTo={} reason=DISABLED",
                    LogSanitizer.safeExcerpt(request.query(), 160), limit, sortBy, request.dateFrom(), request.dateTo());
            return List.of();
        }
        long startNanos = System.nanoTime();
        URI uri = openAlexUri(request, limit, sortBy, properties);
        log.info("literature.search.openalex.request queryExcerpt={} limit={} sortBy={} dateFrom={} dateTo={} urlSummary={}",
                LogSanitizer.safeExcerpt(request.query(), 160), limit, sortBy, request.dateFrom(), request.dateTo(), LogSanitizer.safeUriSummary(uri));
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
            log.info("literature.search.openalex.done queryExcerpt={} limit={} sortBy={} dateFrom={} dateTo={} rawResultCount={} parsedCount={} parseFailedCount={} costMs={}",
                    LogSanitizer.safeExcerpt(request.query(), 160), limit, sortBy, request.dateFrom(), request.dateTo(), rawResultCount, finalResults.size(), parseFailedCount, elapsedMs(startNanos));
            return finalResults;
        } catch (ResourceAccessException ex) {
            if (isTimeout(ex)) {
                log.warn("literature.search.openalex.failed queryExcerpt={} limit={} sortBy={} dateFrom={} dateTo={} reason=OPENALEX_TIMEOUT costMs={}",
                        LogSanitizer.safeExcerpt(request.query(), 160), limit, sortBy, request.dateFrom(), request.dateTo(), elapsedMs(startNanos), ex);
                throw new LiteratureSearchException(HttpStatus.GATEWAY_TIMEOUT, "OPENALEX_TIMEOUT", "OpenAlex 调用超时", ex);
            }
            log.warn("literature.search.openalex.failed queryExcerpt={} limit={} sortBy={} dateFrom={} dateTo={} reason=OPENALEX_CONNECTION_FAILED costMs={}",
                    LogSanitizer.safeExcerpt(request.query(), 160), limit, sortBy, request.dateFrom(), request.dateTo(), elapsedMs(startNanos), ex);
            throw new LiteratureSearchException(HttpStatus.BAD_GATEWAY, "OPENALEX_CONNECTION_FAILED", "无法连接 OpenAlex 文献服务", ex);
        } catch (RestClientException ex) {
            if (isTimeout(ex)) {
                log.warn("literature.search.openalex.failed queryExcerpt={} limit={} sortBy={} dateFrom={} dateTo={} reason=OPENALEX_TIMEOUT costMs={}",
                        LogSanitizer.safeExcerpt(request.query(), 160), limit, sortBy, request.dateFrom(), request.dateTo(), elapsedMs(startNanos), ex);
                throw new LiteratureSearchException(HttpStatus.GATEWAY_TIMEOUT, "OPENALEX_TIMEOUT", "OpenAlex 调用超时", ex);
            }
            log.warn("literature.search.openalex.failed queryExcerpt={} limit={} sortBy={} dateFrom={} dateTo={} reason=OPENALEX_FAILED costMs={}",
                    LogSanitizer.safeExcerpt(request.query(), 160), limit, sortBy, request.dateFrom(), request.dateTo(), elapsedMs(startNanos), ex);
            throw new LiteratureSearchException(HttpStatus.BAD_GATEWAY, "OPENALEX_FAILED", "OpenAlex 文献服务调用失败", ex);
        } catch (RuntimeException ex) {
            if (ex instanceof LiteratureSearchException literatureSearchException) {
                throw literatureSearchException;
            }
            log.warn("literature.search.openalex.parse.failed queryExcerpt={} limit={} sortBy={} dateFrom={} dateTo={} reason=OPENALEX_RESPONSE_INVALID costMs={}",
                    LogSanitizer.safeExcerpt(request.query(), 160), limit, sortBy, request.dateFrom(), request.dateTo(), elapsedMs(startNanos), ex);
            throw new LiteratureSearchException(HttpStatus.BAD_GATEWAY, "OPENALEX_RESPONSE_INVALID", "OpenAlex 文献服务响应无法解析", ex);
        }
    }

    /**
     * 将 OpenAlex 原始 JSON 响应中的 results 数组转换为统一结果列表。
     */
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

    /**
     * 将搜索结果按发表日期倒序排列，缺失日期的结果排在后面。
     */
    List<LiteratureSearchResult> sortByDateDescending(List<LiteratureSearchResult> results) {
        return results.stream()
                .sorted(Comparator.comparing(
                        LiteratureSearchResult::publishedDate,
                        Comparator.nullsLast(Comparator.reverseOrder())
                ))
                .toList();
    }

    /**
     * 将 OpenAlex 的倒排摘要索引还原为自然文本摘要。
     */
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

    /**
     * 将单条 OpenAlex work 节点转换为统一文献搜索结果。
     */
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

    /**
     * 从 OpenAlex authorships 数组中提取作者名称列表。
     */
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

    /**
     * 从 OpenAlex 主题和 concepts 字段中提取分类列表。
     */
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

    /**
     * 向集合中追加非空白文本，并统一去除首尾空白。
     */
    private void addNonBlank(Set<String> items, String value) {
        if (value != null && !value.isBlank()) {
            items.add(value.trim());
        }
    }

    /**
     * 按候选字段顺序读取第一个可用文本值。
     */
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

    /**
     * 返回参数列表中第一个非空白字符串。
     */
    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    /**
     * 将 JSON 数值或数字字符串转换为整数。
     */
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

    /**
     * 将内部排序参数转换为 OpenAlex 支持的排序表达。
     */
    private String openAlexSort(String sortBy) {
        return "relevance_score:desc";
    }

    /**
     * 根据搜索请求、分页数量和配置组装 OpenAlex 请求地址。
     */
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
        String filter = publicationDateFilter(request);
        if (filter != null) {
            builder.queryParam("filter", filter);
        }
        return builder.build().encode().toUri();
    }

    /**
     * 将请求中的起止日期转换为 OpenAlex publication_date 过滤条件。
     */
    private String publicationDateFilter(LiteratureSearchRequest request) {
        List<String> filters = new ArrayList<>();
        if (request.dateFrom() != null && !request.dateFrom().isBlank()) {
            filters.add("from_publication_date:" + request.dateFrom().trim());
        }
        if (request.dateTo() != null && !request.dateTo().isBlank()) {
            filters.add("to_publication_date:" + request.dateTo().trim());
        }
        if (filters.isEmpty()) {
            return null;
        }
        return String.join(",", filters);
    }

    /**
     * 创建带请求超时和指定 JSON 映射器的 RestClient。
     */
    private RestClient client(Duration timeout) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);
        return restClientBuilder
                .requestFactory(requestFactory)
                .messageConverters(converters -> converters.add(0, new org.springframework.http.converter.json.MappingJackson2HttpMessageConverter(objectMapper)))
                .build();
    }

    /**
     * 统计 OpenAlex 原始响应中的结果数量。
     */
    private int rawResultCount(JsonNode raw) {
        if (raw == null || raw.isNull() || raw.isMissingNode()) {
            return 0;
        }
        JsonNode results = raw.path("results");
        return results.isArray() ? results.size() : 0;
    }

    /**
     * 计算从指定起点到当前时间的毫秒耗时。
     */
    private long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    /**
     * 判断异常链路中是否包含 socket 超时。
     */
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
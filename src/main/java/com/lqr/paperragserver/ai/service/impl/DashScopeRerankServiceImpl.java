package com.lqr.paperragserver.ai.service.impl;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.lqr.paperragserver.ai.service.RerankService;
import com.lqr.paperragserver.common.model.RetrievedChunk;
import com.lqr.paperragserver.rag.config.RagProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 基于 DashScope rerank API 的检索候选精排序实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DashScopeRerankServiceImpl implements RerankService {

    private static final String RERANK_PATH = "/api/v1/services/rerank/text-rerank/text-rerank";

    private final RestClient.Builder restClientBuilder;
    private final RagProperties ragProperties;
    private final String apiKey;
    private final boolean configureRequestTimeout;

    /**
     * 注入依赖的构造函数。
     *
     * @param restClientBuilder REST 客户端构建器
     * @param ragProperties RAG 配置属性
     * @param apiKey DashScope API 密钥
     */
    @Autowired
    public DashScopeRerankServiceImpl(RestClient.Builder restClientBuilder,
                                      RagProperties ragProperties,
                                      @Value("${spring.ai.dashscope.api-key:}") String apiKey) {
        this(restClientBuilder, ragProperties, apiKey, true);
    }

    /**
     * 基于 DashScope rerank API 的检索候选精排序实现。
     * @param question 用户问题
     * @param candidates 已完成权限过滤和候选融合的片段
     * @param topN 期望返回数量
     * @return 检索候选片段
     */
    @Override
    public List<RetrievedChunk> rerank(String question, List<RetrievedChunk> candidates, int topN) {
        RagProperties.RerankProperties rerank = ragProperties.rerank();
        long startNanos = System.nanoTime();
        String unavailableReason = unavailableReason(question, candidates, topN);
        if (unavailableReason != null) {
            log.warn("rerank.fallback enabled={} model={} candidateCount={} topN={} fallbackReason={} costMs={}",
                    rerank.enabled(), rerank.model(), candidates == null ? 0 : candidates.size(), topN, unavailableReason, elapsedMs(startNanos));
            return candidates == null ? List.of() : candidates;
        }
        log.info("rerank.start enabled={} model={} candidateCount={} topN={}",
                rerank.enabled(), rerank.model(), candidates.size(), topN);
        try {
            DashScopeRerankResponse response = client(rerank)
                    .post()
                    .uri(RERANK_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .headers(headers -> headers.setBearerAuth(apiKey))
                    .body(request(question, candidates, rerank, topN))
                    .retrieve()
                    .body(DashScopeRerankResponse.class);
            int resultCount = resultCount(response);
            List<RetrievedChunk> reranked = applyResponse(candidates, response, topN);
            log.info("rerank.done enabled={} model={} candidateCount={} topN={} resultCount={} rerankedCount={} costMs={}",
                    rerank.enabled(), rerank.model(), candidates.size(), topN, resultCount, reranked.size(), elapsedMs(startNanos));
            return reranked;
        } catch (RuntimeException ex) {
            log.warn("rerank.fallback enabled={} model={} candidateCount={} topN={} fallbackReason=API_EXCEPTION costMs={}",
                    rerank.enabled(), rerank.model(), candidates.size(), topN, elapsedMs(startNanos), ex);
            return candidates;
        }
    }

    /**
     * 判断 rerank 服务是否可用。
     *
     * @param question 用户问题
     * @param candidates 候选片段列表
     * @param topN 期望返回数量
     * @return 可用时返回 true，否则返回 false
     */
    private boolean available(String question, List<RetrievedChunk> candidates, int topN) {
        return unavailableReason(question, candidates, topN) == null;
    }

    /**
     * 返回 rerank 服务不可用的原因，可用时返回 null。
     *
     * @param question 用户问题
     * @param candidates 候选片段列表
     * @param topN 期望返回数量
     * @return 不可用原因字符串，可用时返回 null
     */
    private String unavailableReason(String question, List<RetrievedChunk> candidates, int topN) {
        if (!ragProperties.rerank().enabled()) {
            return "DISABLED";
        }
        if (question == null || question.isBlank()) {
            return "EMPTY_QUESTION";
        }
        if (candidates == null || candidates.isEmpty()) {
            return "EMPTY_CANDIDATES";
        }
        if (topN <= 0) {
            return "INVALID_TOP_N";
        }
        if (apiKey == null || apiKey.isBlank()) {
            return "MISSING_API_KEY";
        }
        return null;
    }

    /**
     * 构建 DashScope rerank API 的 REST 客户端。
     *
     * @param rerank rerank 配置属性
     * @return 配置好基础地址和超时的 RestClient
     */
    private RestClient client(RagProperties.RerankProperties rerank) {
        RestClient.Builder builder = restClientBuilder.baseUrl(rerank.baseUrl());
        if (configureRequestTimeout) {
            SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
            Duration timeout = rerank.timeout();
            requestFactory.setConnectTimeout(timeout);
            requestFactory.setReadTimeout(timeout);
            builder.requestFactory(requestFactory);
        }
        return builder.build();
    }

    /**
     * 构建 DashScope rerank API 的请求体。
     *
     * @param question 用户问题
     * @param candidates 候选片段列表
     * @param rerank rerank 配置属性
     * @param topN 期望返回数量
     * @return 封装好的 rerank 请求对象
     */
    private DashScopeRerankRequest request(String question,
                                           List<RetrievedChunk> candidates,
                                           RagProperties.RerankProperties rerank,
                                           int topN) {
        List<String> documents = candidates.stream()
                .map(candidate -> candidate.chunk().content())
                .toList();
        return new DashScopeRerankRequest(
                rerank.model(),
                new DashScopeRerankInput(question, documents),
                new DashScopeRerankParameters(Math.min(topN, candidates.size()), true)
        );
    }

    /**
     * 将 rerank API 响应结果应用到原始候选列表，返回重排序后的片段。
     *
     * @param candidates 原始候选片段列表
     * @param response DashScope rerank API 响应
     * @param topN 期望返回数量
     * @return 重排序后的片段列表
     */
    private List<RetrievedChunk> applyResponse(List<RetrievedChunk> candidates,
                                               DashScopeRerankResponse response,
                                               int topN) {
        if (response == null || response.output() == null || response.output().results() == null || response.output().results().isEmpty()) {
            log.warn("rerank.fallback candidateCount={} topN={} fallbackReason=EMPTY_RESPONSE", candidates.size(), topN);
            return candidates;
        }
        List<DashScopeRerankResult> validResults = response.output().results().stream()
                .filter(result -> result.index() >= 0 && result.index() < candidates.size())
                .sorted(Comparator.comparingDouble(DashScopeRerankResult::relevanceScore).reversed())
                .limit(Math.min(topN, candidates.size()))
                .toList();
        if (validResults.isEmpty()) {
            log.warn("rerank.fallback candidateCount={} topN={} resultCount={} fallbackReason=NO_VALID_RESULT",
                    candidates.size(), topN, response.output().results().size());
            return candidates;
        }
        List<RetrievedChunk> reranked = new ArrayList<>();
        Set<Integer> selectedIndexes = new HashSet<>();
        for (DashScopeRerankResult result : validResults) {
            selectedIndexes.add(result.index());
            reranked.add(new RetrievedChunk(candidates.get(result.index()).chunk(), result.relevanceScore()));
        }
        int resultLimit = Math.min(topN, candidates.size());
        for (int index = 0; index < candidates.size() && reranked.size() < resultLimit; index++) {
            if (!selectedIndexes.contains(index)) {
                reranked.add(candidates.get(index));
            }
        }
        return reranked;
    }

    /**
     * 从响应中获取结果数量。
     *
     * @param response DashScope rerank API 响应
     * @return 结果数量，响应为空时返回 0
     */
    private int resultCount(DashScopeRerankResponse response) {
        if (response == null || response.output() == null || response.output().results() == null) {
            return 0;
        }
        return response.output().results().size();
    }

    /**
     * 计算从指定起始时间到当前经过的毫秒数。
     *
     * @param startNanos 起始纳秒时间戳
     * @return 经过的毫秒数
     */
    private long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    private record DashScopeRerankRequest(
            String model,
            DashScopeRerankInput input,
            DashScopeRerankParameters parameters
    ) {
    }

    private record DashScopeRerankInput(
            String query,
            List<String> documents
    ) {
    }

    private record DashScopeRerankParameters(
            @JsonProperty("top_n") int topN,
            @JsonProperty("return_documents") boolean returnDocuments
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record DashScopeRerankResponse(
            DashScopeRerankOutput output
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record DashScopeRerankOutput(
            List<DashScopeRerankResult> results
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record DashScopeRerankResult(
            int index,
            @JsonProperty("relevance_score") double relevanceScore
    ) {
    }
}
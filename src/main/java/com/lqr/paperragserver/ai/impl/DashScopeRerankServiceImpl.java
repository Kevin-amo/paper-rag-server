package com.lqr.paperragserver.ai.impl;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.lqr.paperragserver.ai.service.RerankService;
import com.lqr.paperragserver.common.model.RetrievedChunk;
import com.lqr.paperragserver.config.RagProperties;
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
        if (!available(question, candidates, topN)) {
            return candidates == null ? List.of() : candidates;
        }
        RagProperties.RerankProperties rerank = ragProperties.rerank();
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
            return applyResponse(candidates, response, topN);
        } catch (RuntimeException ex) {
            log.warn("DashScope rerank failed, fallback to fusion ranking", ex);
            return candidates;
        }
    }

    private boolean available(String question, List<RetrievedChunk> candidates, int topN) {
        return ragProperties.rerank().enabled()
                && question != null
                && !question.isBlank()
                && candidates != null
                && !candidates.isEmpty()
                && topN > 0
                && apiKey != null
                && !apiKey.isBlank();
    }

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

    private List<RetrievedChunk> applyResponse(List<RetrievedChunk> candidates,
                                               DashScopeRerankResponse response,
                                               int topN) {
        if (response == null || response.output() == null || response.output().results() == null || response.output().results().isEmpty()) {
            return candidates;
        }
        List<DashScopeRerankResult> validResults = response.output().results().stream()
                .filter(result -> result.index() >= 0 && result.index() < candidates.size())
                .sorted(Comparator.comparingDouble(DashScopeRerankResult::relevanceScore).reversed())
                .limit(Math.min(topN, candidates.size()))
                .toList();
        if (validResults.isEmpty()) {
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
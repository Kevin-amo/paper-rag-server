package com.lqr.paperragserver.rag.service.impl;

import com.lqr.paperragserver.ai.service.RerankService;
import com.lqr.paperragserver.common.constant.MetadataKeys;
import com.lqr.paperragserver.common.model.DocumentChunk;
import com.lqr.paperragserver.common.model.RetrievedChunk;
import com.lqr.paperragserver.rag.config.RagProperties;
import com.lqr.paperragserver.document.service.DocumentPersistenceService;
import com.lqr.paperragserver.rag.service.RagRetrievalService;
import jdk.jfr.Registered;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 基于向量库的检索实现。
 */
@Service
@RequiredArgsConstructor
public class RagRetrievalServiceImpl implements RagRetrievalService {

    private final VectorStore vectorStore;
    private final RagProperties ragProperties;
    private final DocumentPersistenceService documentPersistenceService;
    private final RerankService rerankService;

    /**
     * 根据问题从向量库召回最相关的文档分块。1
     *
     * @param question 用户问题
     * @param topK 召回数量
     * @return 按相关度排序的分块列表
     */
    @Override
    public List<RetrievedChunk> retrieve(UUID ownerUserId, String question, int topK) {
        if (ownerUserId == null || question == null || question.isBlank()) {
            return List.of();
        }
        int resolvedTopK = topK > 0 ? topK : ragProperties.defaultTopK();
        int candidateTopK = rerankCandidateTopK(resolvedTopK);
        SearchRequest.Builder builder = SearchRequest.builder()
                .query(question)
                .topK(candidateTopK);
        double similarityThreshold = ragProperties.similarityThreshold();
        if (similarityThreshold > 0) {
            builder.similarityThreshold(similarityThreshold);
        } else {
            builder.similarityThresholdAll();
        }
        List<Document> documents = vectorStore.similaritySearch(builder.build());
        List<RetrievedChunk> vectorChunks = new ArrayList<>(documents.size());
        int index = 0;
        for (Document document : documents) {
            Map<String, Object> metadata = document.getMetadata();
            String sourceId = metadata == null ? null : String.valueOf(metadata.getOrDefault(MetadataKeys.SOURCE_ID, ""));
            String metadataOwnerUserId = metadata == null ? null : String.valueOf(metadata.getOrDefault(MetadataKeys.OWNER_USER_ID, ""));
            if (!ownerUserId.toString().equals(metadataOwnerUserId) || !isIndexedDocument(ownerUserId, sourceId)) {
                continue;
            }
            String chunkId = metadata == null ? document.getId() : String.valueOf(metadata.getOrDefault(MetadataKeys.CHUNK_ID, document.getId()));
            int currentIndex = index++;
            int chunkIndex = intMetadata(metadata, MetadataKeys.CHUNK_INDEX, currentIndex);
            DocumentChunk chunk = new DocumentChunk(
                    chunkId,
                    sourceId,
                    chunkIndex,
                    document.getText(),
                    metadata
            );
            Double vectorScore = document.getScore();
            vectorChunks.add(new RetrievedChunk(chunk, vectorRankContribution(vectorScore, currentIndex, documents.size())));
        }

        List<DocumentChunk> lexicalChunks = documentPersistenceService.searchChunks(ownerUserId, question, Math.max(resolvedTopK * 3, resolvedTopK));
        Map<String, DocumentChunk> chunkById = new LinkedHashMap<>();
        Map<String, Double> scoreById = new LinkedHashMap<>();
        for (int lexicalIndex = 0; lexicalIndex < lexicalChunks.size(); lexicalIndex++) {
            DocumentChunk chunk = lexicalChunks.get(lexicalIndex);
            chunkById.put(chunk.chunkId(), chunk);
            scoreById.merge(chunk.chunkId(), rankContribution(lexicalIndex, lexicalChunks.size()), Double::sum);
        }
        for (RetrievedChunk chunk : vectorChunks) {
            String chunkId = chunk.chunk().chunkId();
            chunkById.putIfAbsent(chunkId, chunk.chunk());
            scoreById.merge(chunkId, chunk.rankScore(), Double::sum);
        }
        List<RetrievedChunk> fusionRankedCandidates = chunkById.entrySet().stream()
                .map(entry -> new RetrievedChunk(entry.getValue(), scoreById.getOrDefault(entry.getKey(), 0.0)))
                .sorted(Comparator.comparingDouble(RetrievedChunk::rankScore).reversed()
                        .thenComparing(retrieved -> retrieved.chunk().sourceId(), Comparator.nullsLast(String::compareTo))
                        .thenComparingInt(retrieved -> retrieved.chunk().chunkIndex()))
                .limit(candidateTopK)
                .toList();
        return rerankService.rerank(question, fusionRankedCandidates, resolvedTopK).stream()
                .limit(resolvedTopK)
                .toList();
    }

    private int rerankCandidateTopK(int resolvedTopK) {
        int multiplier = ragProperties.rerank().candidateMultiplier();
        return Math.max(resolvedTopK * multiplier, resolvedTopK);
    }

    /**
     * 判断文档是否仍处于可检索的已索引状态。
     *
     * @param sourceId 文档来源 ID
     * @return 文档存在、未删除且状态为已索引时返回 true
     */
    private boolean isIndexedDocument(UUID ownerUserId, String sourceId) {
        if (ownerUserId == null || sourceId == null || sourceId.isBlank()) {
            return false;
        }
        return documentPersistenceService.findDocument(ownerUserId, sourceId)
                .filter(document -> document.deletedAt() == null)
                .map(document -> "INDEXED".equals(document.status()))
                .orElse(false);
    }

    /**
     * 优先使用向量库返回的相似度分数，缺失时按召回排序补评分。
     *
     * @param vectorScore 向量库相似度分数
     * @param index 当前召回位置
     * @param total 召回总数
     * @return 用于融合排序的向量侧分数
     */
    private double vectorRankContribution(Double vectorScore, int index, int total) {
        if (vectorScore != null && vectorScore > 0) {
            return vectorScore;
        }
        return rankContribution(index, total);
    }

    /**
     * 根据排序位置计算递减的归一化贡献分。
     *
     * @param index 当前排序位置
     * @param total 候选总数
     * @return 归一化排序分数
     */
    private double rankContribution(int index, int total) {
        if (total <= 0) {
            return 0.0;
        }
        return (double) (total - index) / total;
    }

    /**
     * 从元数据中解析整数值，解析失败时回退到默认值。
     *
     * @param metadata 元数据映射
     * @param key 目标字段名
     * @param defaultValue 默认值
     * @return 解析出的整数结果
     */
    private int intMetadata(Map<String, Object> metadata, String key, int defaultValue) {
        if (metadata == null) {
            return defaultValue;
        }
        Object value = metadata.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }
}
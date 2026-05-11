package com.lqr.paperragserver.rag.impl;

import com.lqr.paperragserver.common.DocumentChunk;
import com.lqr.paperragserver.common.RetrievedChunk;
import com.lqr.paperragserver.config.RagProperties;
import com.lqr.paperragserver.paper.service.PaperDocumentPersistenceService;
import com.lqr.paperragserver.rag.service.RagRetrievalService;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 基于向量库的检索实现。
 */
@Service
public class RagRetrievalServiceImpl implements RagRetrievalService {

    private final VectorStore vectorStore;
    private final RagProperties ragProperties;
    private final PaperDocumentPersistenceService paperDocumentPersistenceService;

    /**
     * 创建向量检索服务并保存运行配置。
     *
     * @param vectorStore 向量存储实例
     * @param ragProperties 检索相关配置
     */
    public RagRetrievalServiceImpl(VectorStore vectorStore,
                                   RagProperties ragProperties,
                                   PaperDocumentPersistenceService paperDocumentPersistenceService) {
        this.vectorStore = vectorStore;
        this.ragProperties = ragProperties;
        this.paperDocumentPersistenceService = paperDocumentPersistenceService;
    }

    /**
     * 根据问题从向量库召回最相关的文档分块。
     *
     * @param question 用户问题
     * @param topK 召回数量
     * @return 按相关度排序的分块列表
     */
    @Override
    public List<RetrievedChunk> retrieve(String question, int topK) {
        if (question == null || question.isBlank()) {
            return List.of();
        }
        int resolvedTopK = topK > 0 ? topK : ragProperties.defaultTopK();
        SearchRequest.Builder builder = SearchRequest.builder()
                .query(question)
                .topK(resolvedTopK);
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
            String sourceId = metadata == null ? null : String.valueOf(metadata.getOrDefault("sourceId", ""));
            String chunkId = metadata == null ? document.getId() : String.valueOf(metadata.getOrDefault("chunkId", document.getId()));
            int currentIndex = index++;
            int chunkIndex = intMetadata(metadata, "chunkIndex", currentIndex);
            DocumentChunk chunk = new DocumentChunk(
                    chunkId,
                    sourceId,
                    chunkIndex,
                    document.getText(),
                    metadata
            );
            Double score = document.getScore();
            vectorChunks.add(new RetrievedChunk(chunk, score == null ? 0.0 : score));
        }

        List<DocumentChunk> lexicalChunks = paperDocumentPersistenceService.searchChunks(question, Math.max(resolvedTopK * 3, resolvedTopK));
        Map<String, RetrievedChunk> merged = new LinkedHashMap<>();
        int lexicalRank = 0;
        for (DocumentChunk chunk : lexicalChunks) {
            double lexicalScore = Math.max(0.5, 2.0 - (lexicalRank++ * 0.01));
            merged.put(chunk.chunkId(), new RetrievedChunk(chunk, lexicalScore));
        }
        for (RetrievedChunk chunk : vectorChunks) {
            merged.putIfAbsent(chunk.chunk().chunkId(), chunk);
        }
        return merged.values().stream()
                .limit(resolvedTopK)
                .toList();
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
package com.lqr.paperragserver.rag;

import com.lqr.paperragserver.common.DocumentChunk;
import com.lqr.paperragserver.common.RetrievedChunk;
import com.lqr.paperragserver.config.RagProperties;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 基于向量库的检索实现。
 */
@Service
public class VectorStoreRagRetrievalService implements RagRetrievalService {

    private final VectorStore vectorStore;
    private final RagProperties ragProperties;

    public VectorStoreRagRetrievalService(VectorStore vectorStore, RagProperties ragProperties) {
        this.vectorStore = vectorStore;
        this.ragProperties = ragProperties;
    }

    @Override
    public List<RetrievedChunk> retrieve(String question, int topK) {
        if (question == null || question.isBlank()) {
            return List.of();
        }
        SearchRequest.Builder builder = SearchRequest.builder()
                .query(question)
                .topK(topK > 0 ? topK : ragProperties.defaultTopK());
        double similarityThreshold = ragProperties.similarityThreshold();
        if (similarityThreshold > 0) {
            builder.similarityThreshold(similarityThreshold);
        } else {
            builder.similarityThresholdAll();
        }
        List<Document> documents = vectorStore.similaritySearch(builder.build());
        List<RetrievedChunk> chunks = new ArrayList<>(documents.size());
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
            chunks.add(new RetrievedChunk(chunk, score == null ? 0.0 : score));
        }
        return chunks;
    }

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
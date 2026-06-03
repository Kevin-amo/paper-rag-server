package com.lqr.paperragserver.rag;

import com.lqr.paperragserver.ai.service.RerankService;
import com.lqr.paperragserver.common.model.DocumentChunk;
import com.lqr.paperragserver.common.model.RetrievedChunk;
import com.lqr.paperragserver.rag.config.RagProperties;
import com.lqr.paperragserver.document.service.DocumentPersistenceService;
import com.lqr.paperragserver.rag.service.impl.RagRetrievalServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RagRetrievalServiceImplTest {

    private final VectorStore vectorStore = mock(VectorStore.class);
    private final DocumentPersistenceService documentPersistenceService = mock(DocumentPersistenceService.class);
    private final RerankService rerankService = mock(RerankService.class);
    private final RagProperties ragProperties = new RagProperties(800, 120, 5, 0);
    private final UUID ownerUserId = UUID.randomUUID();
    private RagRetrievalServiceImpl service;

    @BeforeEach
    void setUp() {
        when(rerankService.rerank(anyString(), anyList(), anyInt()))
                .thenAnswer(invocation -> invocation.getArgument(1));
        service = new RagRetrievalServiceImpl(vectorStore, ragProperties, documentPersistenceService, rerankService);
    }

    @Test
    void retrieveShouldFallbackToLexicalChunksWhenVectorRecallMissesExactFact() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        DocumentChunk lexicalHit = new DocumentChunk(
                "chunk-0",
                "source-1",
                0,
                "学生姓名：李勤燃 张俊豪 王家豪",
                Map.of("sectionTitle", "前置内容", "title", "课程设计")
        );
        when(documentPersistenceService.searchChunks(ownerUserId, "这篇文章的学生姓名是谁", 9))
                .thenReturn(List.of(lexicalHit));

        List<RetrievedChunk> results = service.retrieve(ownerUserId, "这篇文章的学生姓名是谁", 3);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).chunk().chunkId()).isEqualTo("chunk-0");
        assertThat(results.get(0).rankScore()).isGreaterThan(0d);
        assertThat(results.get(0).chunk().content()).contains("学生姓名");
        verify(documentPersistenceService).searchChunks(ownerUserId, "这篇文章的学生姓名是谁", 9);
        verify(vectorStore).similaritySearch(any(SearchRequest.class));
    }

    @Test
    void retrieveShouldSortByFusionScoreDescending() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(
                new Document("vector-only hit", Map.of(
                        "ownerUserId", ownerUserId.toString(),
                        "chunkId", "chunk-b",
                        "sourceId", "source-1",
                        "chunkIndex", 1,
                        "title", "Paper B"
                ))
        ));
        when(documentPersistenceService.findIndexedDocuments(eq(ownerUserId), any()))
                .thenReturn(Map.of("source-1", Boolean.TRUE));

        DocumentChunk lexicalFirst = new DocumentChunk(
                "chunk-a",
                "source-1",
                0,
                "A content",
                Map.of("title", "Paper A")
        );
        DocumentChunk lexicalSecond = new DocumentChunk(
                "chunk-b",
                "source-1",
                1,
                "B content",
                Map.of("title", "Paper B")
        );
        when(documentPersistenceService.searchChunks(ownerUserId, "排序测试", 9))
                .thenReturn(List.of(lexicalFirst, lexicalSecond));

        List<RetrievedChunk> results = service.retrieve(ownerUserId, "排序测试", 3);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).chunk().chunkId()).isEqualTo("chunk-b");
        assertThat(results.get(0).rankScore()).isGreaterThan(results.get(1).rankScore());
        assertThat(results.get(1).chunk().chunkId()).isEqualTo("chunk-a");
        assertThat(results.get(1).rankScore()).isGreaterThan(0d);
    }

    @Test
    void retrieveShouldUseRerankResultBeforeFinalTopK() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        DocumentChunk lexicalFirst = new DocumentChunk(
                "chunk-a",
                "source-1",
                0,
                "A content",
                Map.of("title", "Paper A")
        );
        DocumentChunk lexicalSecond = new DocumentChunk(
                "chunk-b",
                "source-1",
                1,
                "B content",
                Map.of("title", "Paper B")
        );
        when(documentPersistenceService.searchChunks(ownerUserId, "精排测试", 6))
                .thenReturn(List.of(lexicalFirst, lexicalSecond));
        when(rerankService.rerank(eq("精排测试"), anyList(), eq(2)))
                .thenReturn(List.of(
                        new RetrievedChunk(lexicalSecond, 0.98),
                        new RetrievedChunk(lexicalFirst, 0.12)
                ));

        List<RetrievedChunk> results = service.retrieve(ownerUserId, "精排测试", 2);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).chunk().chunkId()).isEqualTo("chunk-b");
        assertThat(results.get(0).rankScore()).isEqualTo(0.98);
        assertThat(results.get(1).chunk().chunkId()).isEqualTo("chunk-a");
        verify(rerankService).rerank(eq("精排测试"), anyList(), eq(2));
    }

}
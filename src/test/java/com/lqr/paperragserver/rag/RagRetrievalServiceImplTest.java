package com.lqr.paperragserver.rag;

import com.lqr.paperragserver.common.model.DocumentChunk;
import com.lqr.paperragserver.common.model.RetrievedChunk;
import com.lqr.paperragserver.config.RagProperties;
import com.lqr.paperragserver.paper.service.PaperDocumentPersistenceService;
import com.lqr.paperragserver.rag.impl.RagRetrievalServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RagRetrievalServiceImplTest {

    private final VectorStore vectorStore = mock(VectorStore.class);
    private final PaperDocumentPersistenceService paperDocumentPersistenceService = mock(PaperDocumentPersistenceService.class);
    private final RagProperties ragProperties = new RagProperties(800, 120, 5, 0);
    private final UUID ownerUserId = UUID.randomUUID();
    private RagRetrievalServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new RagRetrievalServiceImpl(vectorStore, ragProperties, paperDocumentPersistenceService);
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
        when(paperDocumentPersistenceService.searchChunks(ownerUserId, "这篇文章的学生姓名是谁", 9))
                .thenReturn(List.of(lexicalHit));

        List<RetrievedChunk> results = service.retrieve(ownerUserId, "这篇文章的学生姓名是谁", 3);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).chunk().chunkId()).isEqualTo("chunk-0");
        assertThat(results.get(0).rankScore()).isGreaterThan(0d);
        assertThat(results.get(0).chunk().content()).contains("学生姓名");
        verify(paperDocumentPersistenceService).searchChunks(ownerUserId, "这篇文章的学生姓名是谁", 9);
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
        when(paperDocumentPersistenceService.findDocument(ownerUserId, "source-1"))
                .thenReturn(Optional.of(indexedDocument("source-1")));

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
        when(paperDocumentPersistenceService.searchChunks(ownerUserId, "排序测试", 9))
                .thenReturn(List.of(lexicalFirst, lexicalSecond));

        List<RetrievedChunk> results = service.retrieve(ownerUserId, "排序测试", 3);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).chunk().chunkId()).isEqualTo("chunk-b");
        assertThat(results.get(0).rankScore()).isGreaterThan(results.get(1).rankScore());
        assertThat(results.get(1).chunk().chunkId()).isEqualTo("chunk-a");
        assertThat(results.get(1).rankScore()).isGreaterThan(0d);
    }

    private PaperDocumentPersistenceService.DocumentDetail indexedDocument(String sourceId) {
        OffsetDateTime now = OffsetDateTime.now();
        return new PaperDocumentPersistenceService.DocumentDetail(
                sourceId,
                ownerUserId,
                "Paper",
                "paper.pdf",
                "paper.pdf",
                "application/pdf",
                100L,
                null,
                null,
                null,
                null,
                null,
                null,
                "content",
                Map.of(),
                "INDEXED",
                1,
                null,
                now,
                now,
                null
        );
    }
}
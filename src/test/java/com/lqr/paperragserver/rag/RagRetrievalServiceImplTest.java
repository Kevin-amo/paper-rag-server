package com.lqr.paperragserver.rag;

import com.lqr.paperragserver.common.DocumentChunk;
import com.lqr.paperragserver.common.RetrievedChunk;
import com.lqr.paperragserver.config.RagProperties;
import com.lqr.paperragserver.paper.service.PaperDocumentPersistenceService;
import com.lqr.paperragserver.rag.impl.RagRetrievalServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RagRetrievalServiceImplTest {

    private final VectorStore vectorStore = mock(VectorStore.class);
    private final PaperDocumentPersistenceService paperDocumentPersistenceService = mock(PaperDocumentPersistenceService.class);
    private final RagProperties ragProperties = new RagProperties(800, 120, 5, 0);
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
        when(paperDocumentPersistenceService.searchChunks("这篇文章的学生姓名是谁", 9))
                .thenReturn(List.of(lexicalHit));

        List<RetrievedChunk> results = service.retrieve("这篇文章的学生姓名是谁", 3);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).chunk().chunkId()).isEqualTo("chunk-0");
        assertThat(results.get(0).chunk().content()).contains("学生姓名");
        verify(paperDocumentPersistenceService).searchChunks("这篇文章的学生姓名是谁", 9);
        verify(vectorStore).similaritySearch(any(SearchRequest.class));
    }
}
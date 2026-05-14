package com.lqr.paperragserver.vector;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lqr.paperragserver.ai.service.EmbeddingService;
import com.lqr.paperragserver.common.model.DocumentChunk;
import com.lqr.paperragserver.paper.mapper.PaperDocumentChunkMapper;
import com.lqr.paperragserver.vector.impl.VectorWriteServiceImpl;
import com.lqr.paperragserver.vector.mapper.VectorStoreMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class VectorWriteServiceTest {

    private final VectorStoreMapper vectorStoreMapper = mock(VectorStoreMapper.class);
    private final PaperDocumentChunkMapper chunkMapper = mock(PaperDocumentChunkMapper.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private VectorWriteServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new VectorWriteServiceImpl(vectorStoreMapper, chunkMapper, objectMapper);
        ReflectionTestUtils.setField(service, "embeddingDimensions", 1024);
    }

    @Test
    void upsertShouldWriteVectorAndBackfillChunkReference() {
        DocumentChunk chunk = new DocumentChunk(
                "chunk-1",
                "source-1",
                2,
                "chunk text",
                Map.of("title", "Paper A", "chunkStart", 12, "chunkEnd", 20)
        );
        float[] vector = new float[1024];
        vector[0] = 1.5f;

        service.upsert(List.of(new EmbeddingService.EmbeddingVector(chunk, vector, Map.of("pageNumber", 3))));

        UUID expectedVectorStoreId = UUID.nameUUIDFromBytes("chunk-1".getBytes(StandardCharsets.UTF_8));
        ArgumentCaptor<UUID> idCaptor = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<String> contentCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> metadataCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> embeddingCaptor = ArgumentCaptor.forClass(String.class);
        verify(vectorStoreMapper).upsert(idCaptor.capture(), contentCaptor.capture(), metadataCaptor.capture(), embeddingCaptor.capture());
        verify(chunkMapper).updateVectorStoreId("chunk-1", expectedVectorStoreId);

        assertThat(idCaptor.getValue()).isEqualTo(expectedVectorStoreId);
        assertThat(contentCaptor.getValue()).isEqualTo("chunk text");
        assertThat(metadataCaptor.getValue())
                .contains("\"sourceId\":\"source-1\"")
                .contains("\"chunkId\":\"chunk-1\"")
                .contains("\"chunkIndex\":2")
                .contains("\"pageNumber\":3");
        assertThat(embeddingCaptor.getValue()).startsWith("[1.5,").endsWith("]");
    }

    @Test
    void upsertShouldRejectWrongVectorDimension() {
        DocumentChunk chunk = new DocumentChunk("chunk-1", "source-1", 0, "chunk text", Map.of());
        float[] vector = new float[512];

        assertThatThrownBy(() -> service.upsert(List.of(new EmbeddingService.EmbeddingVector(chunk, vector, Map.of()))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1024");
        verifyNoInteractions(vectorStoreMapper, chunkMapper);
    }

    @Test
    void deleteBySourceIdShouldDelegateToVectorStoreMapper() {
        service.deleteBySourceId("source-1");

        verify(vectorStoreMapper).deleteBySourceId("source-1");
        verifyNoInteractions(chunkMapper);
    }
}
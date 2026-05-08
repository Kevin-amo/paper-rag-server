package com.lqr.paperragserver.document;

import com.lqr.paperragserver.common.DocumentChunk;
import com.lqr.paperragserver.common.DocumentSource;
import com.lqr.paperragserver.config.RagProperties;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultDocumentSplittingServiceTest {

    private final DefaultDocumentSplittingService service = new DefaultDocumentSplittingService(new RagProperties(8, 2, 5, 0));

    @Test
    void splitShouldCreateStableChunksWithOffsets() {
        DocumentSource source = new DocumentSource("source-1", "Paper A", "paper.pdf", Map.of("title", "Paper A"));
        String text = "abcdefghijklmnop";

        List<DocumentChunk> first = service.split(source, text);
        List<DocumentChunk> second = service.split(source, text);

        assertThat(first).hasSize(3);
        assertThat(first).extracting(DocumentChunk::chunkIndex).containsExactly(0, 1, 2);
        assertThat(first).extracting(DocumentChunk::chunkId).containsExactlyElementsOf(second.stream().map(DocumentChunk::chunkId).toList());
        assertThat(first.get(0).metadata())
                .containsEntry("chunkStart", 0)
                .containsEntry("chunkEnd", 8)
                .containsEntry("chunkLength", 8);
        assertThat(first.get(1).metadata())
                .containsEntry("chunkStart", 6)
                .containsEntry("chunkEnd", 14)
                .containsEntry("chunkLength", 8);
        assertThat(first.get(2).metadata())
                .containsEntry("chunkStart", 12)
                .containsEntry("chunkEnd", 16)
                .containsEntry("chunkLength", 4);
    }

    @Test
    void splitShouldReturnEmptyListForBlankText() {
        DocumentSource source = new DocumentSource("source-1", "Paper A", "paper.pdf", Map.of());

        assertThat(service.split(source, "   ")).isEmpty();
    }
}
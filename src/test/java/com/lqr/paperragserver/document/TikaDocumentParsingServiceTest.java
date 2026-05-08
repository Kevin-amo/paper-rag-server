package com.lqr.paperragserver.document;

import com.lqr.paperragserver.common.DocumentSource;
import org.apache.tika.Tika;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TikaDocumentParsingServiceTest {

    private final TikaDocumentParsingService service = new TikaDocumentParsingService(new Tika());

    @Test
    void parseShouldUseProvidedMetadataAndTitle() {
        byte[] content = "hello rag".getBytes(StandardCharsets.UTF_8);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("sourceId", "paper-1");
        metadata.put("title", "Deep Paper");
        metadata.put("author", "Alice");

        DocumentSource source = service.parse("paper.pdf", content, metadata);

        assertThat(source.sourceId()).isEqualTo("paper-1");
        assertThat(source.title()).isEqualTo("Deep Paper");
        assertThat(source.origin()).isEqualTo("paper.pdf");
        assertThat(source.metadata())
                .containsEntry("fileName", "paper.pdf")
                .containsEntry("contentLength", content.length)
                .containsEntry("sourceId", "paper-1")
                .containsEntry("title", "Deep Paper")
                .containsEntry("author", "Alice");
        assertThat(String.valueOf(source.metadata().get("contentType"))).isNotBlank();
    }

    @Test
    void parseShouldGenerateStableSourceIdAndFallbackTitle() {
        byte[] content = "plain text content".getBytes(StandardCharsets.UTF_8);

        DocumentSource first = service.parse("paper.txt", content, Map.of());
        DocumentSource second = service.parse("paper.txt", content, Map.of());

        assertThat(first.sourceId()).isEqualTo(second.sourceId());
        assertThat(first.title()).isEqualTo("paper.txt");
        assertThat(first.origin()).isEqualTo("paper.txt");
        assertThat(first.metadata()).containsEntry("fileName", "paper.txt");
        assertThat(String.valueOf(first.metadata().get("contentType"))).isNotBlank();
    }
}
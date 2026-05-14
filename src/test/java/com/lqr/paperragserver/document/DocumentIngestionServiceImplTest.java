package com.lqr.paperragserver.document;

import com.lqr.paperragserver.ai.service.EmbeddingService;
import com.lqr.paperragserver.common.model.DocumentAsset;
import com.lqr.paperragserver.common.model.DocumentChunk;
import com.lqr.paperragserver.common.model.DocumentIngestionResult;
import com.lqr.paperragserver.common.model.DocumentSource;
import com.lqr.paperragserver.common.model.ParsedDocument;
import com.lqr.paperragserver.document.impl.DocumentIngestionServiceImpl;
import com.lqr.paperragserver.document.service.DocumentParsingService;
import com.lqr.paperragserver.document.service.DocumentSplittingService;
import com.lqr.paperragserver.paper.service.PaperDocumentPersistenceService;
import com.lqr.paperragserver.vector.service.VectorWriteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentIngestionServiceImplTest {

    private final DocumentParsingService documentParsingService = mock(DocumentParsingService.class);
    private final DocumentSplittingService documentSplittingService = mock(DocumentSplittingService.class);
    private final EmbeddingService embeddingService = mock(EmbeddingService.class);
    private final VectorWriteService vectorWriteService = mock(VectorWriteService.class);
    private final PaperDocumentPersistenceService paperDocumentPersistenceService = mock(PaperDocumentPersistenceService.class);
    private DocumentIngestionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DocumentIngestionServiceImpl(
                documentParsingService,
                documentSplittingService,
                embeddingService,
                vectorWriteService,
                paperDocumentPersistenceService
        );
    }

    @Test
    void ingestShouldUseParsedDocumentTextAndMetadata() {
        byte[] content = "scan-bytes".getBytes();
        Map<String, Object> metadata = Map.of("title", "Scan Paper");
        DocumentSource source = new DocumentSource(
                "source-1",
                "Scan Paper",
                "scan.png",
                Map.of(
                        "title", "Scan Paper",
                        "contentType", "image/png",
                        "extractionMode", "MULTIMODAL"
                )
        );
        DocumentAsset asset = new DocumentAsset(
                "asset-1",
                "source-1",
                0,
                "IMAGE",
                "image1.png",
                "image/png",
                10,
                "hash-1",
                "image-data".getBytes(),
                "图片文字",
                0,
                4,
                Map.of("embeddedImagePath", "word/media/image1.png")
        );
        ParsedDocument parsedDocument = new ParsedDocument(source, "页面文本", List.of(asset));
        DocumentChunk chunk = new DocumentChunk(
                "chunk-1",
                "source-1",
                0,
                "页面文本",
                Map.of("sectionTitle", "摘要", "extractionMode", "MULTIMODAL")
        );
        when(documentParsingService.parse("scan.png", content, metadata)).thenReturn(parsedDocument);
        when(documentSplittingService.split(source, "页面文本")).thenReturn(List.of(chunk));
        when(embeddingService.embed(List.of(chunk))).thenReturn(List.of());

        DocumentIngestionResult result = service.ingest("scan.png", content, metadata);

        assertThat(result.source()).isEqualTo(source);
        assertThat(result.chunkCount()).isEqualTo(1);
        verify(paperDocumentPersistenceService).markParsing(source, "页面文本");
        verify(paperDocumentPersistenceService).replaceAssets("source-1", List.of(asset));
        verify(paperDocumentPersistenceService).replaceChunks("source-1", List.of(chunk));
        verify(paperDocumentPersistenceService).markIndexed("source-1", 1);
        verify(vectorWriteService).deleteBySourceId("source-1");
        verify(vectorWriteService).upsert(any());
    }
}
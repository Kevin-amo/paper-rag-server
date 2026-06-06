package com.lqr.paperragserver.document;

import com.lqr.paperragserver.ai.service.EmbeddingService;
import com.lqr.paperragserver.common.model.DocumentAsset;
import com.lqr.paperragserver.common.model.DocumentChunk;
import com.lqr.paperragserver.common.model.DocumentIngestionResult;
import com.lqr.paperragserver.common.model.DocumentSource;
import com.lqr.paperragserver.common.model.ParsedDocument;
import com.lqr.paperragserver.document.config.DocumentIngestionProperties;
import com.lqr.paperragserver.document.event.DocumentIndexedEvent;
import com.lqr.paperragserver.document.service.impl.DocumentIngestionServiceImpl;
import com.lqr.paperragserver.document.service.DocumentParsingService;
import com.lqr.paperragserver.document.service.DocumentSplittingService;
import com.lqr.paperragserver.document.service.DocumentUploadStorageService;
import com.lqr.paperragserver.document.entity.DocumentIngestionJob;
import com.lqr.paperragserver.document.service.DocumentIngestionJobService;
import com.lqr.paperragserver.document.service.DocumentPersistenceService;
import com.lqr.paperragserver.document.structured.service.PaperStructuredParseService;
import com.lqr.paperragserver.vector.service.VectorWriteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 文档入库编排服务的解析、切分、向量写入和状态流转测试。
 */
class DocumentIngestionServiceImplTest {

    private final DocumentParsingService documentParsingService = mock(DocumentParsingService.class);
    private final DocumentSplittingService documentSplittingService = mock(DocumentSplittingService.class);
    private final EmbeddingService embeddingService = mock(EmbeddingService.class);
    private final VectorWriteService vectorWriteService = mock(VectorWriteService.class);
    private final DocumentPersistenceService documentPersistenceService = mock(DocumentPersistenceService.class);
    private final DocumentIngestionJobService documentIngestionJobService = mock(DocumentIngestionJobService.class);
    private final DocumentUploadStorageService documentUploadStorageService = mock(DocumentUploadStorageService.class);
    private final PaperStructuredParseService paperStructuredParseService = mock(PaperStructuredParseService.class);
    private DocumentIngestionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DocumentIngestionServiceImpl(
                documentParsingService,
                documentSplittingService,
                embeddingService,
                vectorWriteService,
                documentPersistenceService,
                documentIngestionJobService,
                documentUploadStorageService,
                new DocumentIngestionProperties("storage", true, 3, new DocumentIngestionProperties.Listener(2, 4), null),
                paperStructuredParseService
        );
    }

    @Test
    void ingestShouldUseParsedDocumentTextAndMetadata() {
        byte[] content = "scan-bytes".getBytes();
        Map<String, Object> metadata = Map.of("title", "Scan Paper");
        Fixture fixture = fixture();
        when(documentParsingService.parse("scan.png", content, metadata)).thenReturn(fixture.parsedDocument());
        when(documentSplittingService.split(fixture.source(), "页面文本")).thenReturn(List.of(fixture.chunk()));
        when(embeddingService.embed(List.of(fixture.chunk()))).thenReturn(List.of());

        UUID ownerUserId = UUID.randomUUID();
        DocumentIngestionResult result = service.ingest(ownerUserId, "scan.png", content, metadata);

        assertThat(result.source()).isEqualTo(fixture.source());
        assertThat(result.chunkCount()).isEqualTo(1);
        verify(documentPersistenceService).markParsing(ownerUserId, fixture.source(), "页面文本");
        verify(documentPersistenceService).replaceAssets(ownerUserId, "source-1", List.of(fixture.asset()));
        verify(documentPersistenceService).replaceChunks(ownerUserId, "source-1", List.of(fixture.chunk()));
        verify(documentPersistenceService).markIndexed(ownerUserId, "source-1", 1);
        verify(vectorWriteService).deleteBySourceId(ownerUserId, "source-1");
        verify(vectorWriteService).upsert(eq(ownerUserId), any());
    }

    @Test
    void processJobShouldUpdateStagesAndMarkIndexed() throws Exception {
        Fixture fixture = fixture();
        UUID ownerUserId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        DocumentIngestionJob job = new DocumentIngestionJob();
        job.setId(jobId);
        job.setOwnerUserId(ownerUserId);
        job.setSourceId("source-1");
        job.setFileName("scan.png");
        job.setTitle("Scan Paper");
        job.setFilePath("storage/scan.png");
        byte[] content = "scan-bytes".getBytes();
        when(documentUploadStorageService.read("storage/scan.png")).thenReturn(content);
        when(documentParsingService.parse(eq("scan.png"), eq(content), any())).thenReturn(fixture.parsedDocument());
        when(documentSplittingService.split(fixture.source(), "页面文本")).thenReturn(List.of(fixture.chunk()));
        when(embeddingService.embed(List.of(fixture.chunk()))).thenReturn(List.of());

        DocumentIngestionResult result = service.processJob(job);

        assertThat(result.chunkCount()).isEqualTo(1);
        verify(documentIngestionJobService).markRunningStage(ownerUserId, jobId, "source-1", DocumentIngestionJobService.STATUS_PARSING, 20);
        verify(documentIngestionJobService).markRunningStage(ownerUserId, jobId, "source-1", DocumentIngestionJobService.STATUS_CHUNKING, 45);
        verify(documentIngestionJobService).markRunningStage(ownerUserId, jobId, "source-1", DocumentIngestionJobService.STATUS_EMBEDDING, 80);
        verify(documentIngestionJobService).markIndexed(ownerUserId, jobId, "source-1");
        verify(documentPersistenceService).markIndexed(ownerUserId, "source-1", 1);
    }

    @Test
    void processJobShouldDeleteUploadFileAfterIndexedWhenKeepUploadFileIsFalse() throws Exception {
        service = new DocumentIngestionServiceImpl(
                documentParsingService,
                documentSplittingService,
                embeddingService,
                vectorWriteService,
                documentPersistenceService,
                documentIngestionJobService,
                documentUploadStorageService,
                new DocumentIngestionProperties("storage", false, 3, new DocumentIngestionProperties.Listener(2, 4), null),
                paperStructuredParseService
        );
        Fixture fixture = fixture();
        UUID ownerUserId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        DocumentIngestionJob job = new DocumentIngestionJob();
        job.setId(jobId);
        job.setOwnerUserId(ownerUserId);
        job.setSourceId("source-1");
        job.setFileName("scan.png");
        job.setFilePath("storage/scan.png");
        byte[] content = "scan-bytes".getBytes();
        when(documentUploadStorageService.read("storage/scan.png")).thenReturn(content);
        when(documentParsingService.parse(eq("scan.png"), eq(content), any())).thenReturn(fixture.parsedDocument());
        when(documentSplittingService.split(fixture.source(), "页面文本")).thenReturn(List.of(fixture.chunk()));
        when(embeddingService.embed(List.of(fixture.chunk()))).thenReturn(List.of());

        service.processJob(job);

        verify(documentUploadStorageService).delete("storage/scan.png");
    }

    private Fixture fixture() {
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
        return new Fixture(source, asset, parsedDocument, chunk);
    }

    private record Fixture(DocumentSource source, DocumentAsset asset, ParsedDocument parsedDocument, DocumentChunk chunk) {
    }
}
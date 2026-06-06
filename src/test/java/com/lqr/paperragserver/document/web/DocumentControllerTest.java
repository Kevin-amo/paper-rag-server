package com.lqr.paperragserver.document.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lqr.paperragserver.auth.entity.SysUser;
import com.lqr.paperragserver.auth.security.SecurityUserPrincipal;
import com.lqr.paperragserver.document.dto.BatchDocumentIngestionItemResponse;
import com.lqr.paperragserver.document.dto.BatchDocumentIngestionResponse;
import com.lqr.paperragserver.document.dto.DocumentJobResponse;
import com.lqr.paperragserver.document.dto.DocumentUploadAcceptedResponse;
import com.lqr.paperragserver.document.entity.DocumentIngestionJob;
import com.lqr.paperragserver.document.model.DocumentIngestionMessage;
import com.lqr.paperragserver.document.service.DocumentIngestionJobService;
import com.lqr.paperragserver.document.service.DocumentIngestionProducer;
import com.lqr.paperragserver.document.service.DocumentIngestionService;
import com.lqr.paperragserver.document.service.DocumentManagementService;
import com.lqr.paperragserver.document.service.DocumentPersistenceService;
import com.lqr.paperragserver.document.service.DocumentUploadStorageService;
import com.lqr.paperragserver.document.structured.entity.PaperStructuredParseEntity;
import com.lqr.paperragserver.document.structured.service.PaperStructuredParseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 文档接口控制器的请求编排、权限归属和响应转换测试。
 */
class DocumentControllerTest {

    private final DocumentIngestionService documentIngestionService = mock(DocumentIngestionService.class);
    private final DocumentManagementService documentManagementService = mock(DocumentManagementService.class);
    private final DocumentPersistenceService documentPersistenceService = mock(DocumentPersistenceService.class);
    private final DocumentIngestionJobService documentIngestionJobService = mock(DocumentIngestionJobService.class);
    private final DocumentUploadStorageService documentUploadStorageService = mock(DocumentUploadStorageService.class);
    private final DocumentIngestionProducer documentIngestionProducer = mock(DocumentIngestionProducer.class);
    private final PaperStructuredParseService paperStructuredParseService = mock(PaperStructuredParseService.class);
    private DocumentController controller;
    private UUID ownerUserId;
    private SecurityUserPrincipal principal;

    @BeforeEach
    void setUp() {
        controller = new DocumentController(
                documentIngestionService,
                documentManagementService,
                documentPersistenceService,
                documentIngestionJobService,
                documentUploadStorageService,
                documentIngestionProducer,
                paperStructuredParseService,
                new ObjectMapper()
        );
        ownerUserId = UUID.randomUUID();
        principal = principal(ownerUserId);
    }

    @Test
    void ingestShouldReturnAcceptedJobWithoutSynchronousIngestion() throws Exception {
        org.springframework.mock.web.MockMultipartFile file = new org.springframework.mock.web.MockMultipartFile(
                "file", "paper-a.pdf", "application/pdf", "a".getBytes()
        );
        DocumentIngestionJob job = job("source-a", "paper-a.pdf", "Paper A", DocumentIngestionJobService.STATUS_QUEUED);
        when(documentUploadStorageService.store(eq(ownerUserId), eq("source-a"), any(UUID.class), eq(file), eq("paper-a.pdf")))
                .thenReturn(new DocumentUploadStorageService.StoredUpload("paper-a.pdf", "storage/paper-a.pdf"));
        when(documentIngestionJobService.createJob(any(UUID.class), eq(ownerUserId), eq("source-a"), eq("paper-a.pdf"), eq("storage/paper-a.pdf"), eq("Paper A"), any()))
                .thenReturn(job);
        when(documentIngestionJobService.findJob(ownerUserId, job.getId())).thenReturn(Optional.of(job));

        ResponseEntity<DocumentUploadAcceptedResponse> response = controller.ingest(principal, file, "source-a", "Paper A");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().jobId()).isEqualTo(job.getId());
        assertThat(response.getBody().sourceId()).isEqualTo("source-a");
        assertThat(response.getBody().status()).isEqualTo(DocumentIngestionJobService.STATUS_QUEUED);
        verify(documentIngestionProducer).publish(new DocumentIngestionMessage(job.getId(), ownerUserId, "source-a"));
        verify(documentIngestionService, never()).ingest(any(), any(), any(), any());
    }

    @Test
    void ingestBatchShouldCreateJobsForEachFile() throws Exception {
        org.springframework.mock.web.MockMultipartFile file1 = new org.springframework.mock.web.MockMultipartFile(
                "files", "paper-a.pdf", "application/pdf", "a".getBytes()
        );
        org.springframework.mock.web.MockMultipartFile file2 = new org.springframework.mock.web.MockMultipartFile(
                "files", "paper-b.pdf", "application/pdf", "b".getBytes()
        );
        DocumentIngestionJob job1 = job("source-a", "paper-a.pdf", "Paper A", DocumentIngestionJobService.STATUS_QUEUED);
        DocumentIngestionJob job2 = job("source-b", "paper-b.pdf", "Paper B", DocumentIngestionJobService.STATUS_QUEUED);
        when(documentUploadStorageService.store(eq(ownerUserId), eq("source-a"), any(UUID.class), eq(file1), eq("paper-a.pdf")))
                .thenReturn(new DocumentUploadStorageService.StoredUpload("paper-a.pdf", "storage/paper-a.pdf"));
        when(documentUploadStorageService.store(eq(ownerUserId), eq("source-b"), any(UUID.class), eq(file2), eq("paper-b.pdf")))
                .thenReturn(new DocumentUploadStorageService.StoredUpload("paper-b.pdf", "storage/paper-b.pdf"));
        when(documentIngestionJobService.createJob(any(UUID.class), eq(ownerUserId), eq("source-a"), eq("paper-a.pdf"), eq("storage/paper-a.pdf"), eq("Paper A"), any()))
                .thenReturn(job1);
        when(documentIngestionJobService.createJob(any(UUID.class), eq(ownerUserId), eq("source-b"), eq("paper-b.pdf"), eq("storage/paper-b.pdf"), eq("Paper B"), any()))
                .thenReturn(job2);
        when(documentIngestionJobService.findJob(ownerUserId, job1.getId())).thenReturn(Optional.of(job1));
        when(documentIngestionJobService.findJob(ownerUserId, job2.getId())).thenReturn(Optional.of(job2));

        String itemsJson = """
                [
                  {"fileName":"paper-a.pdf","sourceId":"source-a","title":"Paper A"},
                  {"fileName":"paper-b.pdf","sourceId":"source-b","title":"Paper B"}
                ]
                """;

        ResponseEntity<BatchDocumentIngestionResponse> response = controller.ingestBatch(
                principal,
                new org.springframework.web.multipart.MultipartFile[]{file1, file2},
                itemsJson
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().acceptedCount()).isEqualTo(2);
        assertThat(response.getBody().failureCount()).isZero();
        assertThat(response.getBody().items()).extracting(BatchDocumentIngestionItemResponse::jobId)
                .containsExactly(job1.getId(), job2.getId());
        verify(documentIngestionProducer, times(2)).publish(any(DocumentIngestionMessage.class));
        verify(documentIngestionService, never()).ingest(any(), any(), any(), any());
    }

    @Test
    void jobShouldReturnJobStatus() {
        DocumentIngestionJob job = job("source-a", "paper-a.pdf", "Paper A", DocumentIngestionJobService.STATUS_FAILED);
        job.setProgress(70);
        job.setErrorMessage("解析失败");
        job.setRetryCount(2);
        when(documentIngestionJobService.findJob(ownerUserId, job.getId())).thenReturn(Optional.of(job));

        DocumentJobResponse response = controller.job(principal, job.getId());

        assertThat(response.jobId()).isEqualTo(job.getId());
        assertThat(response.sourceId()).isEqualTo("source-a");
        assertThat(response.status()).isEqualTo(DocumentIngestionJobService.STATUS_FAILED);
        assertThat(response.progress()).isEqualTo(70);
        assertThat(response.errorMessage()).isEqualTo("解析失败");
        assertThat(response.retryCount()).isEqualTo(2);
    }

    @Test
    void ingestBatchShouldRejectMismatchedItemsCount() {
        org.springframework.mock.web.MockMultipartFile file = new org.springframework.mock.web.MockMultipartFile(
                "files", "paper-a.pdf", "application/pdf", "a".getBytes()
        );

        assertThatThrownBy(() -> controller.ingestBatch(
                principal,
                new org.springframework.web.multipart.MultipartFile[]{file},
                "[]"
        ))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("items 数量必须与文件数量一致");
    }

    @Test
    void structuredParseShouldReturnPersistedResultForAnyOwnedDocument() {
        PaperStructuredParseEntity entity = structuredParse("source-a");
        when(documentPersistenceService.findAnyDocument(ownerUserId, "source-a"))
                .thenReturn(Optional.of(documentDetail("source-a")));
        when(paperStructuredParseService.find(ownerUserId, "source-a"))
                .thenReturn(Optional.of(entity));

        var response = controller.structuredParse(principal, "source-a");

        assertThat(response.sourceId()).isEqualTo("source-a");
        assertThat(response.status()).isEqualTo("COMPLETED");
        assertThat(response.mergedResult()).isEqualTo(Map.of("title", "Paper A", "abstract", "摘要内容"));
        assertThat(response.missingFields()).containsExactly("discussion");
        assertThat(response.lowConfidenceFields()).containsExactly("references");
        assertThat(response.rawModelOutput()).isEqualTo("{\"abstract\":\"摘要内容\"}");
        verify(paperStructuredParseService).find(ownerUserId, "source-a");
    }

    @Test
    void structuredParseStatusShouldReturnCompactStatus() {
        PaperStructuredParseEntity entity = structuredParse("source-a");
        when(documentPersistenceService.findAnyDocument(ownerUserId, "source-a"))
                .thenReturn(Optional.of(documentDetail("source-a")));
        when(paperStructuredParseService.find(ownerUserId, "source-a"))
                .thenReturn(Optional.of(entity));

        var response = controller.structuredParseStatus(principal, "source-a");

        assertThat(response.sourceId()).isEqualTo("source-a");
        assertThat(response.status()).isEqualTo("COMPLETED");
        assertThat(response.missingFields()).containsExactly("discussion");
        assertThat(response.lowConfidenceFields()).containsExactly("references");
        assertThat(response.errorMessage()).isNull();
    }

    @Test
    void regenerateStructuredParseShouldRequireOwnedDocumentAndDelegate() {
        PaperStructuredParseEntity entity = structuredParse("source-a");
        when(documentPersistenceService.findAnyDocument(ownerUserId, "source-a"))
                .thenReturn(Optional.of(documentDetail("source-a")));
        when(paperStructuredParseService.regenerate(ownerUserId, "source-a"))
                .thenReturn(entity);

        var response = controller.regenerateStructuredParse(principal, "source-a");

        assertThat(response.sourceId()).isEqualTo("source-a");
        assertThat(response.status()).isEqualTo("COMPLETED");
        verify(paperStructuredParseService).regenerate(ownerUserId, "source-a");
    }

    @Test
    void structuredParseShouldRejectMissingDocumentBeforeServiceLookup() {
        when(documentPersistenceService.findAnyDocument(ownerUserId, "missing-source"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.structuredParse(principal, "missing-source"))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("文档不存在");
        verify(paperStructuredParseService, never()).find(any(), any());
        verify(paperStructuredParseService, never()).regenerate(any(), any());
    }

    @Test
    void deleteBySourceIdShouldDelegateToIngestionService() {
        controller.deleteBySourceId(principal, "source-1");

        verify(documentIngestionService).deleteBySourceId(ownerUserId, "source-1");
    }

    private DocumentIngestionJob job(String sourceId, String fileName, String title, String status) {
        DocumentIngestionJob job = new DocumentIngestionJob();
        job.setId(UUID.randomUUID());
        job.setOwnerUserId(ownerUserId);
        job.setSourceId(sourceId);
        job.setFileName(fileName);
        job.setTitle(title);
        job.setFilePath("storage/" + fileName);
        job.setStatus(status);
        job.setProgress(DocumentIngestionJobService.STATUS_QUEUED.equals(status) ? 5 : 0);
        job.setRetryCount(0);
        job.setCreatedAt(OffsetDateTime.now());
        job.setUpdatedAt(OffsetDateTime.now());
        return job;
    }

    private PaperStructuredParseEntity structuredParse(String sourceId) {
        PaperStructuredParseEntity entity = new PaperStructuredParseEntity();
        entity.setId(UUID.randomUUID());
        entity.setOwnerUserId(ownerUserId);
        entity.setDocumentId(UUID.randomUUID());
        entity.setSourceId(sourceId);
        entity.setStatus("COMPLETED");
        entity.setRuleResult(Map.of("title", "Paper A"));
        entity.setModelResult(Map.of("abstract", "摘要内容"));
        entity.setMergedResult(Map.of("title", "Paper A", "abstract", "摘要内容"));
        entity.setFieldConfidence(Map.of("title", Map.of("source", "RULE", "confidence", 0.9)));
        entity.setMissingFields(List.of("discussion"));
        entity.setLowConfidenceFields(List.of("references"));
        entity.setRawModelOutput("{\"abstract\":\"摘要内容\"}");
        entity.setParsedAt(OffsetDateTime.now());
        entity.setUpdatedAt(OffsetDateTime.now());
        return entity;
    }

    private DocumentPersistenceService.DocumentDetail documentDetail(String sourceId) {
        return new DocumentPersistenceService.DocumentDetail(
                sourceId,
                ownerUserId,
                "Paper A",
                "upload",
                "paper-a.pdf",
                "application/pdf",
                1024L,
                List.of("Author A"),
                "摘要内容",
                null,
                null,
                2026,
                List.of("keyword"),
                "论文全文",
                Map.of(),
                "INDEXED",
                3,
                null,
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                null
        );
    }

    private SecurityUserPrincipal principal(UUID userId) {
        SysUser user = new SysUser();
        user.setId(userId);
        user.setUsername("user");
        user.setPasswordHash("{noop}password");
        user.setDisplayName("User");
        user.setStatus("ACTIVE");
        return new SecurityUserPrincipal(user, List.of("USER"));
    }
}
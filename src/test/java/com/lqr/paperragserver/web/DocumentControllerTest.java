package com.lqr.paperragserver.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lqr.paperragserver.common.model.DocumentIngestionResult;
import com.lqr.paperragserver.common.model.DocumentSource;
import com.lqr.paperragserver.document.service.DocumentIngestionService;
import com.lqr.paperragserver.document.DocumentManagementService;
import com.lqr.paperragserver.paper.service.PaperDocumentPersistenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentControllerTest {

    private final DocumentIngestionService documentIngestionService = mock(DocumentIngestionService.class);
    private final DocumentManagementService documentManagementService = mock(DocumentManagementService.class);
    private final PaperDocumentPersistenceService paperDocumentPersistenceService = mock(PaperDocumentPersistenceService.class);
    private DocumentController controller;

    @BeforeEach
    void setUp() {
        controller = new DocumentController(
                documentIngestionService,
                documentManagementService,
                paperDocumentPersistenceService,
                new ObjectMapper()
        );
    }

    @Test
    void ingestBatchShouldReturnPerFileResultsForSuccessAndFailure() {
        org.springframework.mock.web.MockMultipartFile file1 = new org.springframework.mock.web.MockMultipartFile(
                "files", "paper-a.pdf", "application/pdf", "a".getBytes()
        );
        org.springframework.mock.web.MockMultipartFile file2 = new org.springframework.mock.web.MockMultipartFile(
                "files", "paper-b.pdf", "application/pdf", "b".getBytes()
        );

        when(documentIngestionService.ingest(eq("paper-a.pdf"), any(), eq(Map.of("sourceId", "source-a", "title", "Paper A"))))
                .thenReturn(new DocumentIngestionResult(
                        new DocumentSource("source-a", "Paper A", "paper-a.pdf", Map.of("sourceId", "source-a")),
                        3
                ));
        doThrow(new IllegalStateException("解析失败"))
                .when(documentIngestionService)
                .ingest(eq("paper-b.pdf"), any(), eq(Map.of("title", "Paper B")));

        String itemsJson = """
                [
                  {"fileName":"paper-a.pdf","sourceId":"source-a","title":"Paper A"},
                  {"fileName":"paper-b.pdf","title":"Paper B"}
                ]
                """;

        DocumentController.BatchDocumentIngestionResponse response = controller.ingestBatch(
                new org.springframework.web.multipart.MultipartFile[]{file1, file2},
                itemsJson
        );

        assertThat(response.successCount()).isEqualTo(1);
        assertThat(response.failureCount()).isEqualTo(1);
        assertThat(response.items()).hasSize(2);

        DocumentController.BatchDocumentIngestionItemResponse successItem = response.items().get(0);
        assertThat(successItem.index()).isEqualTo(0);
        assertThat(successItem.success()).isTrue();
        assertThat(successItem.fileName()).isEqualTo("paper-a.pdf");
        assertThat(successItem.chunkCount()).isEqualTo(3);
        assertThat(successItem.source()).isNotNull();
        assertThat(successItem.source().sourceId()).isEqualTo("source-a");

        DocumentController.BatchDocumentIngestionItemResponse failureItem = response.items().get(1);
        assertThat(failureItem.index()).isEqualTo(1);
        assertThat(failureItem.success()).isFalse();
        assertThat(failureItem.fileName()).isEqualTo("paper-b.pdf");
        assertThat(failureItem.errorMessage()).isEqualTo("解析失败");
        assertThat(failureItem.source()).isNull();

        verify(documentIngestionService, times(2)).ingest(any(), any(), any());
    }

    @Test
    void ingestBatchShouldRejectMismatchedItemsCount() {
        org.springframework.mock.web.MockMultipartFile file = new org.springframework.mock.web.MockMultipartFile(
                "files", "paper-a.pdf", "application/pdf", "a".getBytes()
        );

        assertThatThrownBy(() -> controller.ingestBatch(
                new org.springframework.web.multipart.MultipartFile[]{file},
                "[]"
        ))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("items 数量必须与文件数量一致");
    }

    @Test
    void deleteBySourceIdShouldDelegateToIngestionService() {
        controller.deleteBySourceId("source-1");

        verify(documentIngestionService).deleteBySourceId("source-1");
    }
}
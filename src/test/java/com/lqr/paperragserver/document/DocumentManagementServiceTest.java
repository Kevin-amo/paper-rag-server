package com.lqr.paperragserver.document;

import com.lqr.paperragserver.ai.EmbeddingService;
import com.lqr.paperragserver.paper.PaperDocumentPersistenceService;
import com.lqr.paperragserver.vector.VectorWriteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class DocumentManagementServiceTest {

    private final PaperDocumentPersistenceService paperDocumentPersistenceService = mock(PaperDocumentPersistenceService.class);
    private final DocumentSplittingService documentSplittingService = mock(DocumentSplittingService.class);
    private final EmbeddingService embeddingService = mock(EmbeddingService.class);
    private final VectorWriteService vectorWriteService = mock(VectorWriteService.class);
    private DocumentManagementService service;

    @BeforeEach
    void setUp() {
        service = new DocumentManagementService(
                paperDocumentPersistenceService,
                documentSplittingService,
                embeddingService,
                vectorWriteService
        );
    }

    @Test
    void restoreShouldDelegateToPersistenceLayer() {
        service.restore("source-1");

        verify(paperDocumentPersistenceService).restore("source-1");
    }
}
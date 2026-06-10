package com.lqr.papermind.document;

import com.lqr.papermind.ai.service.EmbeddingService;
import com.lqr.papermind.document.service.impl.DocumentManagementServiceImpl;
import com.lqr.papermind.document.service.DocumentManagementService;
import com.lqr.papermind.document.service.DocumentSplittingService;
import com.lqr.papermind.document.service.DocumentPersistenceService;
import com.lqr.papermind.vector.service.VectorWriteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * 文档管理服务的恢复和重建索引编排测试。
 */
class DocumentManagementServiceTest {

    private final DocumentPersistenceService documentPersistenceService = mock(DocumentPersistenceService.class);
    private final DocumentSplittingService documentSplittingService = mock(DocumentSplittingService.class);
    private final EmbeddingService embeddingService = mock(EmbeddingService.class);
    private final VectorWriteService vectorWriteService = mock(VectorWriteService.class);
    private DocumentManagementService service;

    @BeforeEach
    void setUp() {
        service = new DocumentManagementServiceImpl(
                documentPersistenceService,
                documentSplittingService,
                embeddingService,
                vectorWriteService
        );
    }

    @Test
    void restoreShouldDelegateToPersistenceLayer() {
        UUID ownerUserId = UUID.randomUUID();

        service.restore(ownerUserId, "source-1");

        verify(documentPersistenceService).restore(ownerUserId, "source-1");
    }
}
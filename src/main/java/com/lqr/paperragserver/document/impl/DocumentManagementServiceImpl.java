package com.lqr.paperragserver.document.impl;

import com.lqr.paperragserver.ai.service.EmbeddingService;
import com.lqr.paperragserver.common.model.DocumentChunk;
import com.lqr.paperragserver.common.model.DocumentSource;
import com.lqr.paperragserver.document.service.DocumentManagementService;
import com.lqr.paperragserver.document.service.DocumentSplittingService;
import com.lqr.paperragserver.paper.service.PaperDocumentPersistenceService;
import com.lqr.paperragserver.vector.service.VectorWriteService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DocumentManagementServiceImpl implements DocumentManagementService {

    private final PaperDocumentPersistenceService paperDocumentPersistenceService;
    private final DocumentSplittingService documentSplittingService;
    private final EmbeddingService embeddingService;
    private final VectorWriteService vectorWriteService;

    @Override
    public void restore(UUID ownerUserId, String sourceId) {
        paperDocumentPersistenceService.restore(ownerUserId, sourceId);
    }

    @Override
    @Transactional
    public ReindexResult reindex(UUID ownerUserId, String sourceId) {
        PaperDocumentPersistenceService.DocumentDetail document = paperDocumentPersistenceService.findDocument(ownerUserId, sourceId)
                .orElseThrow(() -> new IllegalArgumentException("文档不存在: " + sourceId));
        if (document.contentText() == null || document.contentText().isBlank()) {
            paperDocumentPersistenceService.markFailed(ownerUserId, sourceId, "文档全文为空，无法重建索引");
            throw new IllegalStateException("文档全文为空，无法重建索引");
        }
        try {
            DocumentSource source = new DocumentSource(
                    document.sourceId(),
                    document.title(),
                    document.origin(),
                    document.metadata() == null ? Map.of() : document.metadata()
            );
            List<DocumentChunk> chunks = documentSplittingService.split(source, document.contentText());
            vectorWriteService.deleteBySourceId(ownerUserId, sourceId);
            paperDocumentPersistenceService.replaceChunks(ownerUserId, sourceId, chunks);
            vectorWriteService.upsert(ownerUserId, embeddingService.embed(chunks));
            paperDocumentPersistenceService.markIndexed(ownerUserId, sourceId, chunks.size());
            return new ReindexResult(sourceId, chunks.size());
        } catch (RuntimeException ex) {
            paperDocumentPersistenceService.markFailed(ownerUserId, sourceId, ex.getMessage());
            throw ex;
        }
    }
}
package com.lqr.paperragserver.document;

import com.lqr.paperragserver.ai.service.EmbeddingService;
import com.lqr.paperragserver.common.DocumentChunk;
import com.lqr.paperragserver.common.DocumentSource;
import com.lqr.paperragserver.document.service.DocumentSplittingService;
import com.lqr.paperragserver.paper.service.PaperDocumentPersistenceService;
import com.lqr.paperragserver.vector.service.VectorWriteService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 文档管理编排服务。
 */
@Service
@RequiredArgsConstructor
public class DocumentManagementService {

    private final PaperDocumentPersistenceService paperDocumentPersistenceService;
    private final DocumentSplittingService documentSplittingService;
    private final EmbeddingService embeddingService;
    private final VectorWriteService vectorWriteService;

    public void restore(String sourceId) {
        paperDocumentPersistenceService.restore(sourceId);
    }

    @Transactional
    public ReindexResult reindex(String sourceId) {
        PaperDocumentPersistenceService.DocumentDetail document = paperDocumentPersistenceService.findDocument(sourceId)
                .orElseThrow(() -> new IllegalArgumentException("文档不存在: " + sourceId));
        if (document.contentText() == null || document.contentText().isBlank()) {
            paperDocumentPersistenceService.markFailed(sourceId, "文档全文为空，无法重建索引");
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
            vectorWriteService.deleteBySourceId(sourceId);
            paperDocumentPersistenceService.replaceChunks(sourceId, chunks);
            vectorWriteService.upsert(embeddingService.embed(chunks));
            paperDocumentPersistenceService.markIndexed(sourceId, chunks.size());
            return new ReindexResult(sourceId, chunks.size());
        } catch (RuntimeException ex) {
            paperDocumentPersistenceService.markFailed(sourceId, ex.getMessage());
            throw ex;
        }
    }

    public record ReindexResult(String sourceId, int chunkCount) {
    }
}
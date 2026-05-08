package com.lqr.paperragserver.document;

import com.lqr.paperragserver.ai.EmbeddingService;
import com.lqr.paperragserver.common.DocumentChunk;
import com.lqr.paperragserver.common.DocumentIngestionResult;
import com.lqr.paperragserver.common.DocumentSource;
import com.lqr.paperragserver.common.ParsedDocument;
import com.lqr.paperragserver.paper.PaperDocumentPersistenceService;
import com.lqr.paperragserver.vector.VectorWriteService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 默认文档入库编排实现。
 */
@Service
public class DefaultDocumentIngestionService implements DocumentIngestionService {

    private final DocumentParsingService documentParsingService;
    private final DocumentSplittingService documentSplittingService;
    private final EmbeddingService embeddingService;
    private final VectorWriteService vectorWriteService;
    private final PaperDocumentPersistenceService paperDocumentPersistenceService;

    public DefaultDocumentIngestionService(DocumentParsingService documentParsingService,
                                           DocumentSplittingService documentSplittingService,
                                           EmbeddingService embeddingService,
                                           VectorWriteService vectorWriteService,
                                           PaperDocumentPersistenceService paperDocumentPersistenceService) {
        this.documentParsingService = documentParsingService;
        this.documentSplittingService = documentSplittingService;
        this.embeddingService = embeddingService;
        this.vectorWriteService = vectorWriteService;
        this.paperDocumentPersistenceService = paperDocumentPersistenceService;
    }

    @Override
    public DocumentIngestionResult ingest(String fileName, byte[] content, Map<String, Object> metadata) {
        DocumentSource source = documentParsingService.parse(fileName, content, metadata);
        try {
            paperDocumentPersistenceService.markParsing(source, null);
            String text = extractText(content);
            paperDocumentPersistenceService.markParsing(source, text);
            List<DocumentChunk> chunks = documentSplittingService.split(source, text);
            vectorWriteService.deleteBySourceId(source.sourceId());
            paperDocumentPersistenceService.replaceChunks(source.sourceId(), chunks);
            vectorWriteService.upsert(embeddingService.embed(chunks));
            paperDocumentPersistenceService.markIndexed(source.sourceId(), chunks.size());
            return new DocumentIngestionResult(source, chunks.size());
        } catch (RuntimeException ex) {
            paperDocumentPersistenceService.markFailed(source.sourceId(), ex.getMessage());
            throw ex;
        }
    }

    @Override
    public void deleteBySourceId(String sourceId) {
        vectorWriteService.deleteBySourceId(sourceId);
        paperDocumentPersistenceService.markDeleted(sourceId);
    }

    private String extractText(byte[] content) {
        if (documentParsingService instanceof TikaDocumentParsingService tikaParsingService) {
            return tikaParsingService.extractText(content);
        }
        ParsedDocument parsedDocument = new ParsedDocument(new DocumentSource("unknown", "unknown", "unknown", Map.of()), "");
        return parsedDocument.text();
    }
}
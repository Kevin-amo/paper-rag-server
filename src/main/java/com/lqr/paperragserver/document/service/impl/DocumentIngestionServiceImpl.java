package com.lqr.paperragserver.document.service.impl;

import com.lqr.paperragserver.ai.service.EmbeddingService;
import com.lqr.paperragserver.common.constant.MetadataKeys;
import com.lqr.paperragserver.common.model.DocumentChunk;
import com.lqr.paperragserver.common.model.DocumentIngestionResult;
import com.lqr.paperragserver.common.model.DocumentSource;
import com.lqr.paperragserver.common.model.ParsedDocument;
import com.lqr.paperragserver.document.config.DocumentIngestionProperties;
import com.lqr.paperragserver.document.service.DocumentIngestionService;
import com.lqr.paperragserver.document.service.DocumentParsingService;
import com.lqr.paperragserver.document.service.DocumentSplittingService;
import com.lqr.paperragserver.document.service.DocumentUploadStorageService;
import com.lqr.paperragserver.document.entity.DocumentIngestionJob;
import com.lqr.paperragserver.document.service.DocumentIngestionJobService;
import com.lqr.paperragserver.document.service.DocumentPersistenceService;
import com.lqr.paperragserver.vector.service.VectorWriteService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 默认文档入库编排实现。
 */
@Service
@RequiredArgsConstructor
public class DocumentIngestionServiceImpl implements DocumentIngestionService {

    private final DocumentParsingService documentParsingService;
    private final DocumentSplittingService documentSplittingService;
    private final EmbeddingService embeddingService;
    private final VectorWriteService vectorWriteService;
    private final DocumentPersistenceService documentPersistenceService;
    private final DocumentIngestionJobService documentIngestionJobService;
    private final DocumentUploadStorageService documentUploadStorageService;
    private final DocumentIngestionProperties documentIngestionProperties;

    /**
     * 执行文档解析、切分、持久化和向量写入的完整入库流程。
     *
     * @param fileName 上传文件名
     * @param content 文件二进制内容
     * @param metadata 额外元数据
     * @return 入库后的文档结果摘要
     */
    @Override
    public DocumentIngestionResult ingest(UUID ownerUserId, String fileName, byte[] content, Map<String, Object> metadata) {
        ParsedDocument parsedDocument = documentParsingService.parse(fileName, content, metadata);
        return processParsedDocument(ownerUserId, parsedDocument);
    }

    @Override
    public DocumentIngestionResult processJob(DocumentIngestionJob job) {
        try {
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put(MetadataKeys.SOURCE_ID, job.getSourceId());
            metadata.put(MetadataKeys.FILE_NAME, job.getFileName());
            if (job.getTitle() != null && !job.getTitle().isBlank()) {
                metadata.put(MetadataKeys.TITLE, job.getTitle());
            }
            byte[] content = documentUploadStorageService.read(job.getFilePath());
            documentIngestionJobService.markRunningStage(
                    job.getOwnerUserId(), job.getId(), job.getSourceId(), DocumentIngestionJobService.STATUS_PARSING, 20
            );
            ParsedDocument parsedDocument = documentParsingService.parse(job.getFileName(), content, metadata);
            DocumentIngestionResult result = processParsedDocument(job, parsedDocument);
            if (!documentIngestionProperties.keepUploadFile()) {
                documentUploadStorageService.delete(job.getFilePath());
            }
            return result;
        } catch (IOException ex) {
            String message = ex.getMessage() == null ? "读取上传文件失败" : ex.getMessage();
            documentIngestionJobService.markFailed(job.getOwnerUserId(), job.getId(), job.getSourceId(), message);
            throw new IllegalStateException("读取上传文件失败", ex);
        } catch (RuntimeException ex) {
            documentIngestionJobService.markFailed(job.getOwnerUserId(), job.getId(), job.getSourceId(), ex.getMessage());
            throw ex;
        }
    }

    private DocumentIngestionResult processParsedDocument(UUID ownerUserId, ParsedDocument parsedDocument) {
        DocumentSource source = parsedDocument.source();
        String text = parsedDocument.text();
        try {
            documentPersistenceService.markParsing(ownerUserId, source, text);
            documentPersistenceService.replaceAssets(ownerUserId, source.sourceId(), parsedDocument.assets());
            List<DocumentChunk> chunks = documentSplittingService.split(source, text);
            vectorWriteService.deleteBySourceId(ownerUserId, source.sourceId());
            documentPersistenceService.replaceChunks(ownerUserId, source.sourceId(), chunks);
            vectorWriteService.upsert(ownerUserId, embeddingService.embed(chunks));
            documentPersistenceService.markIndexed(ownerUserId, source.sourceId(), chunks.size());
            return new DocumentIngestionResult(source, chunks.size());
        } catch (RuntimeException ex) {
            documentPersistenceService.markFailed(ownerUserId, source.sourceId(), ex.getMessage());
            throw ex;
        }
    }

    private DocumentIngestionResult processParsedDocument(DocumentIngestionJob job, ParsedDocument parsedDocument) {
        DocumentSource source = parsedDocument.source();
        String text = parsedDocument.text();
        documentIngestionJobService.markRunningStage(
                job.getOwnerUserId(), job.getId(), source.sourceId(), DocumentIngestionJobService.STATUS_PARSING, 30
        );
        documentPersistenceService.markParsing(job.getOwnerUserId(), source, text);
        documentPersistenceService.replaceAssets(job.getOwnerUserId(), source.sourceId(), parsedDocument.assets());

        documentIngestionJobService.markRunningStage(
                job.getOwnerUserId(), job.getId(), source.sourceId(), DocumentIngestionJobService.STATUS_CHUNKING, 45
        );
        List<DocumentChunk> chunks = documentSplittingService.split(source, text);

        documentIngestionJobService.markRunningStage(
                job.getOwnerUserId(), job.getId(), source.sourceId(), DocumentIngestionJobService.STATUS_INDEXING, 65
        );
        vectorWriteService.deleteBySourceId(job.getOwnerUserId(), source.sourceId());
        documentPersistenceService.replaceChunks(job.getOwnerUserId(), source.sourceId(), chunks);

        documentIngestionJobService.markRunningStage(
                job.getOwnerUserId(), job.getId(), source.sourceId(), DocumentIngestionJobService.STATUS_EMBEDDING, 80
        );
        List<EmbeddingService.EmbeddingVector> vectors = embeddingService.embed(chunks);

        documentIngestionJobService.markRunningStage(
                job.getOwnerUserId(), job.getId(), source.sourceId(), DocumentIngestionJobService.STATUS_INDEXING, 90
        );
        vectorWriteService.upsert(job.getOwnerUserId(), vectors);
        documentPersistenceService.markIndexed(job.getOwnerUserId(), source.sourceId(), chunks.size());
        documentIngestionJobService.markIndexed(job.getOwnerUserId(), job.getId(), source.sourceId());
        return new DocumentIngestionResult(source, chunks.size());
    }

    /**
     * 按来源标识删除文档关联的向量和持久化状态。
     *
     * @param sourceId 文档来源标识
     */
    @Override
    public void deleteBySourceId(UUID ownerUserId, String sourceId) {
        vectorWriteService.deleteBySourceId(ownerUserId, sourceId);
        documentPersistenceService.markDeleted(ownerUserId, sourceId);
    }
}
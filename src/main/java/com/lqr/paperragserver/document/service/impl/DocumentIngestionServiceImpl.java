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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 默认文档入库编排实现。
 */
@Slf4j
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
        long startNanos = System.nanoTime();
        log.info("document.ingest.start ownerUserId={} sourceId={} fileName={} fileSize={}",
                ownerUserId, metadataValue(metadata, MetadataKeys.SOURCE_ID), fileName, contentLength(content));
        try {
            ParsedDocument parsedDocument = documentParsingService.parse(fileName, content, metadata);
            log.info("document.ingest.parse.done ownerUserId={} sourceId={} fileName={} textLength={} assetCount={} costMs={}",
                    ownerUserId, parsedDocument.source().sourceId(), fileName, textLength(parsedDocument.text()), assetCount(parsedDocument), elapsedMs(startNanos));
            DocumentIngestionResult result = processParsedDocument(ownerUserId, parsedDocument);
            log.info("document.ingest.done ownerUserId={} sourceId={} fileName={} chunkCount={} costMs={}",
                    ownerUserId, result.source().sourceId(), fileName, result.chunkCount(), elapsedMs(startNanos));
            return result;
        } catch (RuntimeException ex) {
            log.error("document.ingest.failed ownerUserId={} sourceId={} fileName={} fileSize={} costMs={}",
                    ownerUserId, metadataValue(metadata, MetadataKeys.SOURCE_ID), fileName, contentLength(content), elapsedMs(startNanos), ex);
            throw ex;
        }
    }

    @Override
    public DocumentIngestionResult processJob(DocumentIngestionJob job) {
        long startNanos = System.nanoTime();
        log.info("document.ingest.start ownerUserId={} jobId={} sourceId={} fileName={}",
                job.getOwnerUserId(), job.getId(), job.getSourceId(), job.getFileName());
        try {
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put(MetadataKeys.SOURCE_ID, job.getSourceId());
            metadata.put(MetadataKeys.FILE_NAME, job.getFileName());
            if (job.getTitle() != null && !job.getTitle().isBlank()) {
                metadata.put(MetadataKeys.TITLE, job.getTitle());
            }
            byte[] content = documentUploadStorageService.read(job.getFilePath());
            log.info("document.ingest.read.done ownerUserId={} jobId={} sourceId={} fileName={} fileSize={} costMs={}",
                    job.getOwnerUserId(), job.getId(), job.getSourceId(), job.getFileName(), contentLength(content), elapsedMs(startNanos));
            markJobStage(job, DocumentIngestionJobService.STATUS_PARSING, 20);
            ParsedDocument parsedDocument = documentParsingService.parse(job.getFileName(), content, metadata);
            log.info("document.ingest.parse.done ownerUserId={} jobId={} sourceId={} fileName={} textLength={} assetCount={} costMs={}",
                    job.getOwnerUserId(), job.getId(), parsedDocument.source().sourceId(), job.getFileName(), textLength(parsedDocument.text()), assetCount(parsedDocument), elapsedMs(startNanos));
            DocumentIngestionResult result = processParsedDocument(job, parsedDocument, startNanos);
            if (!documentIngestionProperties.keepUploadFile()) {
                documentUploadStorageService.delete(job.getFilePath());
            }
            log.info("document.ingest.done ownerUserId={} jobId={} sourceId={} fileName={} chunkCount={} costMs={}",
                    job.getOwnerUserId(), job.getId(), result.source().sourceId(), job.getFileName(), result.chunkCount(), elapsedMs(startNanos));
            return result;
        } catch (IOException ex) {
            String message = ex.getMessage() == null ? "读取上传文件失败" : ex.getMessage();
            documentIngestionJobService.markFailed(job.getOwnerUserId(), job.getId(), job.getSourceId(), message);
            log.error("document.ingest.failed ownerUserId={} jobId={} sourceId={} fileName={} stage=READ costMs={}",
                    job.getOwnerUserId(), job.getId(), job.getSourceId(), job.getFileName(), elapsedMs(startNanos), ex);
            throw new IllegalStateException("读取上传文件失败", ex);
        } catch (RuntimeException ex) {
            documentIngestionJobService.markFailed(job.getOwnerUserId(), job.getId(), job.getSourceId(), ex.getMessage());
            log.error("document.ingest.failed ownerUserId={} jobId={} sourceId={} fileName={} costMs={}",
                    job.getOwnerUserId(), job.getId(), job.getSourceId(), job.getFileName(), elapsedMs(startNanos), ex);
            throw ex;
        }
    }

    private DocumentIngestionResult processParsedDocument(UUID ownerUserId, ParsedDocument parsedDocument) {
        DocumentSource source = parsedDocument.source();
        String text = parsedDocument.text();
        try {
            documentPersistenceService.markParsing(ownerUserId, source, text);
            log.info("document.ingest.status ownerUserId={} sourceId={} status=PARSING progress={}", ownerUserId, source.sourceId(), 30);
            documentPersistenceService.replaceAssets(ownerUserId, source.sourceId(), parsedDocument.assets());
            List<DocumentChunk> chunks = documentSplittingService.split(source, text);
            log.info("document.ingest.split.done ownerUserId={} sourceId={} textLength={} chunkCount={}",
                    ownerUserId, source.sourceId(), textLength(text), chunks.size());
            vectorWriteService.deleteBySourceId(ownerUserId, source.sourceId());
            documentPersistenceService.replaceChunks(ownerUserId, source.sourceId(), chunks);
            List<EmbeddingService.EmbeddingVector> vectors = embeddingService.embed(chunks);
            log.info("document.ingest.embed.done ownerUserId={} sourceId={} chunkCount={} vectorCount={}",
                    ownerUserId, source.sourceId(), chunks.size(), vectors.size());
            vectorWriteService.upsert(ownerUserId, vectors);
            log.info("document.ingest.vector.done ownerUserId={} sourceId={} vectorCount={}", ownerUserId, source.sourceId(), vectors.size());
            documentPersistenceService.markIndexed(ownerUserId, source.sourceId(), chunks.size());
            log.info("document.ingest.status ownerUserId={} sourceId={} status=INDEXED progress={}", ownerUserId, source.sourceId(), 100);
            return new DocumentIngestionResult(source, chunks.size());
        } catch (RuntimeException ex) {
            documentPersistenceService.markFailed(ownerUserId, source.sourceId(), ex.getMessage());
            log.info("document.ingest.status ownerUserId={} sourceId={} status=FAILED progress={}", ownerUserId, source.sourceId(), 100);
            throw ex;
        }
    }

    private DocumentIngestionResult processParsedDocument(DocumentIngestionJob job, ParsedDocument parsedDocument, long startNanos) {
        DocumentSource source = parsedDocument.source();
        String text = parsedDocument.text();
        markJobStage(job, DocumentIngestionJobService.STATUS_PARSING, 30);
        documentPersistenceService.markParsing(job.getOwnerUserId(), source, text);
        documentPersistenceService.replaceAssets(job.getOwnerUserId(), source.sourceId(), parsedDocument.assets());

        markJobStage(job, DocumentIngestionJobService.STATUS_CHUNKING, 45);
        List<DocumentChunk> chunks = documentSplittingService.split(source, text);
        log.info("document.ingest.split.done ownerUserId={} jobId={} sourceId={} textLength={} chunkCount={} costMs={}",
                job.getOwnerUserId(), job.getId(), source.sourceId(), textLength(text), chunks.size(), elapsedMs(startNanos));

        markJobStage(job, DocumentIngestionJobService.STATUS_INDEXING, 65);
        vectorWriteService.deleteBySourceId(job.getOwnerUserId(), source.sourceId());
        documentPersistenceService.replaceChunks(job.getOwnerUserId(), source.sourceId(), chunks);

        markJobStage(job, DocumentIngestionJobService.STATUS_EMBEDDING, 80);
        List<EmbeddingService.EmbeddingVector> vectors = embeddingService.embed(chunks);
        log.info("document.ingest.embed.done ownerUserId={} jobId={} sourceId={} chunkCount={} vectorCount={} costMs={}",
                job.getOwnerUserId(), job.getId(), source.sourceId(), chunks.size(), vectors.size(), elapsedMs(startNanos));

        markJobStage(job, DocumentIngestionJobService.STATUS_INDEXING, 90);
        vectorWriteService.upsert(job.getOwnerUserId(), vectors);
        log.info("document.ingest.vector.done ownerUserId={} jobId={} sourceId={} vectorCount={} costMs={}",
                job.getOwnerUserId(), job.getId(), source.sourceId(), vectors.size(), elapsedMs(startNanos));
        documentPersistenceService.markIndexed(job.getOwnerUserId(), source.sourceId(), chunks.size());
        documentIngestionJobService.markIndexed(job.getOwnerUserId(), job.getId(), source.sourceId());
        log.info("document.ingest.status ownerUserId={} jobId={} sourceId={} status=INDEXED progress={}",
                job.getOwnerUserId(), job.getId(), source.sourceId(), 100);
        return new DocumentIngestionResult(source, chunks.size());
    }

    private void markJobStage(DocumentIngestionJob job, String status, int progress) {
        documentIngestionJobService.markRunningStage(job.getOwnerUserId(), job.getId(), job.getSourceId(), status, progress);
        log.info("document.ingest.status ownerUserId={} jobId={} sourceId={} status={} progress={}",
                job.getOwnerUserId(), job.getId(), job.getSourceId(), status, progress);
    }

    private long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    private int contentLength(byte[] content) {
        return content == null ? 0 : content.length;
    }

    private int textLength(String text) {
        return text == null ? 0 : text.length();
    }

    private int assetCount(ParsedDocument parsedDocument) {
        return parsedDocument.assets() == null ? 0 : parsedDocument.assets().size();
    }

    private Object metadataValue(Map<String, Object> metadata, String key) {
        return metadata == null ? null : metadata.get(key);
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
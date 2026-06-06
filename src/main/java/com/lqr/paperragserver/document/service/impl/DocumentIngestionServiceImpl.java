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
import com.lqr.paperragserver.document.structured.service.PaperStructuredParseService;
import com.lqr.paperragserver.vector.service.VectorWriteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final PaperStructuredParseService paperStructuredParseService;

    /**
     * 执行文档解析、切分、持久化和向量写入的完整入库流程。
     *
     * @param fileName 上传文件名
     * @param content 文件二进制内容
     * @param metadata 额外元数据
     * @return 入库后的文档结果摘要
     */
    @Override
    @Transactional
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

    /**
     * 处理已持久化的异步入库任务，执行完整的解析、切分、嵌入和向量写入流程。
     *
     * @param job 入库任务
     * @return 入库结果
     * @throws IllegalStateException 读取上传文件失败时抛出
     */
    @Override
    @Transactional
    public DocumentIngestionResult processJob(DocumentIngestionJob job) {
        long startNanos = System.nanoTime();
        log.info("document.ingest.start ownerUserId={} jobId={} sourceId={} fileName={}",
                job.getOwnerUserId(), job.getId(), job.getSourceId(), job.getFileName());
        try {
            Map<String, Object> metadata = new LinkedHashMap<>();
            documentPersistenceService.findAnyDocument(job.getOwnerUserId(), job.getSourceId())
                    .map(DocumentPersistenceService.DocumentDetail::metadata)
                    .ifPresent(metadata::putAll);
            metadata.put(MetadataKeys.SOURCE_ID, job.getSourceId());
            metadata.put(MetadataKeys.FILE_NAME, job.getFileName());
            metadata.putIfAbsent(MetadataKeys.SOURCE_TYPE, MetadataKeys.SOURCE_TYPE_USER);
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

    /**
     * 对同步入库的解析结果执行切分、嵌入和向量写入流程。
     *
     * @param ownerUserId 文档所属用户 ID
     * @param parsedDocument 解析后的文档
     * @return 入库结果
     */
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
            generateStructuredParseQuietly(ownerUserId, source.sourceId());
            log.info("document.ingest.status ownerUserId={} sourceId={} status=INDEXED progress={}", ownerUserId, source.sourceId(), 100);
            return new DocumentIngestionResult(source, chunks.size());
        } catch (RuntimeException ex) {
            documentPersistenceService.markFailed(ownerUserId, source.sourceId(), ex.getMessage());
            log.info("document.ingest.status ownerUserId={} sourceId={} status=FAILED progress={}", ownerUserId, source.sourceId(), 100);
            throw ex;
        }
    }

    /**
     * 对异步入库的解析结果执行切分、嵌入和向量写入流程，同时更新任务阶段进度。
     *
     * @param job 入库任务
     * @param parsedDocument 解析后的文档
     * @param startNanos 入库开始时间（纳秒）
     * @return 入库结果
     */
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

    /**
     * 同步入库完成后触发结构化解析，失败时不影响文档入库结果。
     */
    private void generateStructuredParseQuietly(UUID ownerUserId, String sourceId) {
        try {
            paperStructuredParseService.generate(ownerUserId, sourceId);
        } catch (RuntimeException ex) {
            log.warn("paper.structured.parse.sync.failed ownerUserId={} sourceId={}", ownerUserId, sourceId, ex);
        }
    }

    /**
     * 更新任务运行阶段并记录日志。
     *
     * @param job 入库任务
     * @param status 当前阶段状态
     * @param progress 进度百分比
     */
    private void markJobStage(DocumentIngestionJob job, String status, int progress) {
        documentIngestionJobService.markRunningStage(job.getOwnerUserId(), job.getId(), job.getSourceId(), status, progress);
        log.info("document.ingest.status ownerUserId={} jobId={} sourceId={} status={} progress={}",
                job.getOwnerUserId(), job.getId(), job.getSourceId(), status, progress);
    }

    /**
     * 计算从指定起始时间到当前的耗时（毫秒）。
     *
     * @param startNanos 起始时间（纳秒）
     * @return 耗时毫秒数
     */
    private long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    /**
     * 安全获取二进制内容长度。
     *
     * @param content 二进制内容
     * @return 内容长度
     */
    private int contentLength(byte[] content) {
        return content == null ? 0 : content.length;
    }

    /**
     * 安全获取文本长度。
     *
     * @param text 文本内容
     * @return 文本长度
     */
    private int textLength(String text) {
        return text == null ? 0 : text.length();
    }

    /**
     * 安全获取文档资产数量。
     *
     * @param parsedDocument 解析后的文档
     * @return 资产数量
     */
    private int assetCount(ParsedDocument parsedDocument) {
        return parsedDocument.assets() == null ? 0 : parsedDocument.assets().size();
    }

    /**
     * 从元数据映射中安全获取指定键的值。
     *
     * @param metadata 元数据映射
     * @param key 键名
     * @return 对应的值，不存在时返回 null
     */
    private Object metadataValue(Map<String, Object> metadata, String key) {
        return metadata == null ? null : metadata.get(key);
    }

    /**
     * 按来源标识删除文档关联的向量和持久化状态。
     *
     * @param sourceId 文档来源标识
     */
    @Override
    @Transactional
    public void deleteBySourceId(UUID ownerUserId, String sourceId) {
        documentPersistenceService.markDeleted(ownerUserId, sourceId);
        vectorWriteService.deleteUserVectorsBySourceId(ownerUserId, sourceId);
    }

    /**
     * 删除当前用户的全部文档数据，包括持久化状态和向量索引。
     *
     * @param ownerUserId 文档所属用户 ID
     */
    @Override
    @Transactional
    public void deleteAll(UUID ownerUserId) {
        documentPersistenceService.markAllDeleted(ownerUserId);
        vectorWriteService.deleteUserVectorsByOwnerUserId(ownerUserId);
    }
}
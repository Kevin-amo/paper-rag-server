package com.lqr.paperragserver.document.service.impl;

import com.lqr.paperragserver.ai.service.EmbeddingService;
import com.lqr.paperragserver.common.model.DocumentChunk;
import com.lqr.paperragserver.common.model.DocumentSource;
import com.lqr.paperragserver.document.service.DocumentManagementService;
import com.lqr.paperragserver.document.service.DocumentSplittingService;
import com.lqr.paperragserver.document.service.DocumentPersistenceService;
import com.lqr.paperragserver.vector.service.VectorWriteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 文档管理服务实现，负责编排文档恢复和重建索引流程。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentManagementServiceImpl implements DocumentManagementService {

    private final DocumentPersistenceService documentPersistenceService;
    private final DocumentSplittingService documentSplittingService;
    private final EmbeddingService embeddingService;
    private final VectorWriteService vectorWriteService;

    /**
     * 恢复指定文档的可见状态。
     *
     * @param ownerUserId 文档所属用户 ID
     * @param sourceId 文档来源标识
     */
    @Override
    public void restore(UUID ownerUserId, String sourceId) {
        documentPersistenceService.restore(ownerUserId, sourceId);
        log.info("document.restore.done ownerUserId={} sourceId={}", ownerUserId, sourceId);
    }

    /**
     * 基于已持久化的文档全文重建分块和向量索引。
     *
     * @param ownerUserId 文档所属用户 ID
     * @param sourceId 文档来源标识
     * @return 重建后的分块统计结果
     * @throws IllegalArgumentException 文档不存在时抛出
     * @throws IllegalStateException 文档全文为空时抛出
     */
    @Override
    @Transactional
    public ReindexResult reindex(UUID ownerUserId, String sourceId) {
        long startNanos = System.nanoTime();
        log.info("document.reindex.start ownerUserId={} sourceId={}", ownerUserId, sourceId);
        DocumentPersistenceService.DocumentDetail document = documentPersistenceService.findDocument(ownerUserId, sourceId)
                .orElseThrow(() -> new IllegalArgumentException("文档不存在: " + sourceId));
        if (document.contentText() == null || document.contentText().isBlank()) {
            documentPersistenceService.markFailed(ownerUserId, sourceId, "文档全文为空，无法重建索引");
            log.warn("document.reindex.failed ownerUserId={} sourceId={} reason=EMPTY_CONTENT contentLength={}",
                    ownerUserId, sourceId, contentLength(document.contentText()));
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
            documentPersistenceService.replaceChunks(ownerUserId, sourceId, chunks);
            List<EmbeddingService.EmbeddingVector> vectors = embeddingService.embed(chunks);
            vectorWriteService.upsert(ownerUserId, vectors);
            documentPersistenceService.markIndexed(ownerUserId, sourceId, chunks.size());
            log.info("document.reindex.done ownerUserId={} sourceId={} contentLength={} chunkCount={} vectorCount={} costMs={}",
                    ownerUserId, sourceId, contentLength(document.contentText()), chunks.size(), vectors.size(), elapsedMs(startNanos));
            return new ReindexResult(sourceId, chunks.size());
        } catch (RuntimeException ex) {
            documentPersistenceService.markFailed(ownerUserId, sourceId, ex.getMessage());
            log.error("document.reindex.failed ownerUserId={} sourceId={} contentLength={} costMs={}",
                    ownerUserId, sourceId, contentLength(document.contentText()), elapsedMs(startNanos), ex);
            throw ex;
        }
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
     * 安全获取文本内容长度。
     *
     * @param content 文本内容
     * @return 内容长度
     */
    private int contentLength(String content) {
        return content == null ? 0 : content.length();
    }
}
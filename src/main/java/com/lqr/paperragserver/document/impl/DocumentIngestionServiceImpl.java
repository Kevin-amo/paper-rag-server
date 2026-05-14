package com.lqr.paperragserver.document.impl;

import com.lqr.paperragserver.ai.service.EmbeddingService;
import com.lqr.paperragserver.common.model.DocumentChunk;
import com.lqr.paperragserver.common.model.DocumentIngestionResult;
import com.lqr.paperragserver.common.model.DocumentSource;
import com.lqr.paperragserver.common.model.ParsedDocument;
import com.lqr.paperragserver.document.service.DocumentIngestionService;
import com.lqr.paperragserver.document.service.DocumentParsingService;
import com.lqr.paperragserver.document.service.DocumentSplittingService;
import com.lqr.paperragserver.paper.service.PaperDocumentPersistenceService;
import com.lqr.paperragserver.vector.service.VectorWriteService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

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
    private final PaperDocumentPersistenceService paperDocumentPersistenceService;

    /**
     * 执行文档解析、切分、持久化和向量写入的完整入库流程。
     *
     * @param fileName 上传文件名
     * @param content 文件二进制内容
     * @param metadata 额外元数据
     * @return 入库后的文档结果摘要
     */
    @Override
    public DocumentIngestionResult ingest(String fileName, byte[] content, Map<String, Object> metadata) {
        ParsedDocument parsedDocument = documentParsingService.parse(fileName, content, metadata);
        DocumentSource source = parsedDocument.source();
        String text = parsedDocument.text();
        try {
            // 解析文件，得到全文文本 text
            paperDocumentPersistenceService.markParsing(source, text);
            paperDocumentPersistenceService.replaceAssets(source.sourceId(), parsedDocument.assets());
            // 切分，保存到列表chunks
            List<DocumentChunk> chunks = documentSplittingService.split(source, text);
            // 删除旧向量，替换 chunk 表
            vectorWriteService.deleteBySourceId(source.sourceId());
            paperDocumentPersistenceService.replaceChunks(source.sourceId(), chunks);
            // 对每个 chunk 生成 embedding 写入vector_store
            vectorWriteService.upsert(embeddingService.embed(chunks));
            // 更新文档状态 INDEXED
            paperDocumentPersistenceService.markIndexed(source.sourceId(), chunks.size());
            return new DocumentIngestionResult(source, chunks.size());
        } catch (RuntimeException ex) {
            // 出现异常就标记为 FAILED
            paperDocumentPersistenceService.markFailed(source.sourceId(), ex.getMessage());
            throw ex;
        }
    }

    /**
     * 按来源标识删除文档关联的向量和持久化状态。
     *
     * @param sourceId 文档来源标识
     */
    @Override
    public void deleteBySourceId(String sourceId) {
        vectorWriteService.deleteBySourceId(sourceId);
        paperDocumentPersistenceService.markDeleted(sourceId);
    }
}
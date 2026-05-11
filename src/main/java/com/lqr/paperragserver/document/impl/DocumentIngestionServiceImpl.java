package com.lqr.paperragserver.document.impl;

import com.lqr.paperragserver.ai.service.EmbeddingService;
import com.lqr.paperragserver.common.DocumentChunk;
import com.lqr.paperragserver.common.DocumentIngestionResult;
import com.lqr.paperragserver.common.DocumentSource;
import com.lqr.paperragserver.common.ParsedDocument;
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

    /**
     * 通过具体解析实现提取正文文本。
     *
     * @param content 原始文件字节
     * @return 提取出的正文文本，无法委派时返回空字符串
     */
    private String extractText(byte[] content) {
        if (documentParsingService instanceof DocumentParsingServiceImpl tikaParsingService) {
            return tikaParsingService.extractText(content);
        }
        ParsedDocument parsedDocument = new ParsedDocument(new DocumentSource("unknown", "unknown", "unknown", Map.of()), "");
        return parsedDocument.text();
    }
}
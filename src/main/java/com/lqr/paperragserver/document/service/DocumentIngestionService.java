package com.lqr.paperragserver.document.service;

import com.lqr.paperragserver.common.model.DocumentIngestionResult;
import com.lqr.paperragserver.document.entity.DocumentIngestionJob;

import java.util.Map;
import java.util.UUID;

/**
 * 文档入库编排服务。
 *
 * <p>该服务把解析、切分、Embedding 和向量写入串起来，形成完整的文档入库链路。</p>
 */
public interface DocumentIngestionService {

    /**
     * 入库一个文档文件。
     *
     * @param fileName 原始文件名
     * @param content 文件二进制内容
     * @param metadata 额外元数据
     * @return 入库结果
     */
    DocumentIngestionResult ingest(UUID ownerUserId, String fileName, byte[] content, Map<String, Object> metadata);

    /**
     * 处理已持久化的异步入库任务。
     *
     * @param job 入库任务
     * @return 入库结果
     */
    DocumentIngestionResult processJob(DocumentIngestionJob job);

    /**
     * 根据来源标识删除文档对应的向量数据。
     *
     * @param sourceId 文档来源标识
     */
    void deleteBySourceId(UUID ownerUserId, String sourceId);

    /**
     * 删除当前用户的全部文档数据。
     */
    void deleteAll(UUID ownerUserId);
}
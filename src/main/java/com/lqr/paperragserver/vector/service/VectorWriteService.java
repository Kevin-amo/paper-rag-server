package com.lqr.paperragserver.vector.service;

import com.lqr.paperragserver.ai.service.EmbeddingService;

import java.util.List;
import java.util.UUID;

/**
 * 向量写入服务接口。
 *
 * <p>实现类负责把文档片段的向量批量写入向量库，并支持按来源删除旧数据。</p>
 */
public interface VectorWriteService {

    /**
     * 批量写入或更新向量记录。
     *
     * @param vectors 片段向量结果列表
     */
    void upsert(UUID ownerUserId, List<EmbeddingService.EmbeddingVector> vectors);

    /**
     * 按文档来源删除向量记录。
     *
     * @param sourceId 文档来源标识
     */
    void deleteBySourceId(UUID ownerUserId, String sourceId);

    /**
     * 按用户知识库文档来源删除向量记录。
     * @param sourceId 文档来源标识
     */
    void deleteUserVectorsBySourceId(UUID ownerUserId, String sourceId);

    /**
     * 删除当前用户的全部用户知识库向量记录。
     */
    void deleteUserVectorsByOwnerUserId(UUID ownerUserId);
}
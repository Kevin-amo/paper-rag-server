package com.lqr.paperragserver.document.service;

import java.util.UUID;

/**
 * 文档管理编排服务。
 */
public interface DocumentManagementService {

    /**
     * 恢复指定文档的可见状态。
     *
     * @param ownerUserId 文档所属用户 ID
     * @param sourceId 文档来源 ID
     */
    void restore(UUID ownerUserId, String sourceId);

    /**
     * 基于已持久化的文档全文重建分块和向量索引。
     *
     * @param ownerUserId 文档所属用户 ID
     * @param sourceId 文档来源 ID
     * @return 重建后的分块统计结果
     */
    ReindexResult reindex(UUID ownerUserId, String sourceId);

    record ReindexResult(String sourceId, int chunkCount) {
    }
}
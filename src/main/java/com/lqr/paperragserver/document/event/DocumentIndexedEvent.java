package com.lqr.paperragserver.document.event;

import java.util.UUID;

/**
 * 文档异步入库索引完成事件。
 */
public record DocumentIndexedEvent(
        UUID ownerUserId,
        UUID jobId,
        String sourceId
) {
}
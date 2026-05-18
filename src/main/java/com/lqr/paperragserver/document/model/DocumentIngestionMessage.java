package com.lqr.paperragserver.document.model;

import java.util.UUID;

/**
 * 文档入库 MQ 消息。
 */
public record DocumentIngestionMessage(
        UUID jobId,
        UUID ownerUserId,
        String sourceId
) {
}
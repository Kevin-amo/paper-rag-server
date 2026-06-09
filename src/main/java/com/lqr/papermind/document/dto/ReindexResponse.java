package com.lqr.papermind.document.dto;

/**
 * 文档重建索引操作的响应结果。
 */
public record ReindexResponse(String sourceId, int chunkCount) {
}
package com.lqr.papermind.common.model;

/**
 * 文档入库结果。
 *
 * @param source 已入库文档来源信息
 * @param chunkCount 实际写入的片段数量
 */
public record DocumentIngestionResult(
        DocumentSource source,
        int chunkCount
) {
}
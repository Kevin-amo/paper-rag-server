package com.lqr.paperragserver.common.model;

import java.util.Map;

/**
 * 文档来源信息。
 *
 * @param sourceId 文档来源唯一标识，用于关联切分片段和向量记录
 * @param title 文档标题，通常来自文件名或论文元数据
 * @param origin 文档来源位置，例如上传文件名、URL 或外部系统 ID
 * @param metadata 文档级扩展元数据，例如作者、年份、期刊等信息
 */
public record DocumentSource(
        String sourceId,
        String title,
        String origin,
        Map<String, Object> metadata
) {
}
package com.lqr.paperragserver.common;

/**
 * 解析后的文档内容。
 *
 * @param source 文档来源信息
 * @param text 提取出的正文文本
 */
public record ParsedDocument(
        DocumentSource source,
        String text
) {
}
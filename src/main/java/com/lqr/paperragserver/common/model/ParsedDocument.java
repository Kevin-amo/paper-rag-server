package com.lqr.paperragserver.common.model;

import java.util.List;

/**
 * 解析后的文档内容。
 *
 * @param source 文档来源信息
 * @param text 提取出的正文文本
 * @param assets 解析过程中提取出的二进制资产
 */
public record ParsedDocument(
        DocumentSource source,
        String text,
        List<DocumentAsset> assets
) {
    /**
     * 创建不包含二进制资产的解析结果。
     *
     * @param source 文档来源信息
     * @param text 提取出的正文文本
     */
    public ParsedDocument(DocumentSource source, String text) {
        this(source, text, List.of());
    }
}
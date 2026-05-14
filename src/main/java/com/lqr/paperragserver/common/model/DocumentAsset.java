package com.lqr.paperragserver.common.model;

import java.util.Map;

/**
 * 文档解析过程中提取出的二进制资产，例如 Word 内嵌图片。
 *
 * @param assetId 资产唯一标识
 * @param sourceId 所属文档来源标识
 * @param assetIndex 资产在文档中的顺序号
 * @param assetType 资产类型，例如 IMAGE
 * @param fileName 原始文件名或解析生成的文件名
 * @param contentType MIME 类型
 * @param fileSize 二进制大小
 * @param contentHash 内容 SHA-256
 * @param content 资产二进制内容
 * @param extractedText 从资产中抽取出的文本
 * @param textStart 资产 OCR 文本块在解析全文中的起始偏移
 * @param textEnd 资产 OCR 文本块在解析全文中的结束偏移
 * @param metadata 资产级扩展元数据
 */
public record DocumentAsset(
        String assetId,
        String sourceId,
        int assetIndex,
        String assetType,
        String fileName,
        String contentType,
        long fileSize,
        String contentHash,
        byte[] content,
        String extractedText,
        Integer textStart,
        Integer textEnd,
        Map<String, Object> metadata
) {
}
package com.lqr.paperragserver.document.service;

import com.lqr.paperragserver.common.model.DocumentSource;

/**
 * 原生多模态文档抽取服务。
 *
 * <p>用于把图片或扫描版 PDF 页面交给支持多模态的模型提取可检索文本。</p>
 */
public interface DocumentMultimodalExtractionService {

    /**
     * 从图片或扫描版 PDF 中提取文本。
     *
     * @param source 文档来源信息
     * @param content 原始文件二进制内容
     * @return 提取结果
     */
    DocumentMultimodalExtractionResult extract(DocumentSource source, byte[] content);

    /**
     * 多模态抽取结果。
     *
     * @param text 提取出的纯文本
     * @param pageCount 参与抽取的页面数量；图片场景为 1
     * @param truncated 是否因为页数限制而截断
     */
    record DocumentMultimodalExtractionResult(String text, int pageCount, boolean truncated) {
    }
}
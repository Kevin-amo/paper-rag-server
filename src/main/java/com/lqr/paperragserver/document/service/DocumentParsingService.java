package com.lqr.paperragserver.document.service;

import com.lqr.paperragserver.common.DocumentSource;

import java.util.Map;

/**
 * 文档解析服务接口。
 *
 * <p>实现类负责从文件内容中识别文档来源信息和基础元数据，具体解析方式可以基于 Tika、PDFBox 或其他解析器。</p>
 */
public interface DocumentParsingService {

    /**
     * 解析上传或导入的文档。
     *
     * @param fileName 原始文件名
     * @param content 文件二进制内容
     * @param metadata 调用方传入的补充元数据
     * @return 文档来源信息
     */
    DocumentSource parse(String fileName, byte[] content, Map<String, Object> metadata);
}
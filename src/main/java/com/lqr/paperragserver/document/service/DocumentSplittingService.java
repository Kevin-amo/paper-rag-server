package com.lqr.paperragserver.document.service;

import com.lqr.paperragserver.common.model.DocumentChunk;
import com.lqr.paperragserver.common.model.DocumentSource;

import java.util.List;

/**
 * 文档切分服务接口。
 *
 * <p>实现类负责把完整文本切分成适合 Embedding 和向量检索的片段，并保留页码、章节等回溯信息。</p>
 */
public interface DocumentSplittingService {

    /**
     * 将完整文档文本切分为多个片段。
     *
     * @param source 文档来源信息
     * @param fullText 文档完整文本
     * @return 可写入向量库的文本片段列表
     */
    List<DocumentChunk> split(DocumentSource source, String fullText);
}
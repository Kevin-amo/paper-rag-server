package com.lqr.paperragserver.paper;

import com.lqr.paperragserver.common.model.DocumentSource;

import java.util.Map;

/**
 * 论文元数据服务接口。
 *
 * <p>实现类负责把作者、发表年份、期刊、关键词等论文领域信息合并到统一的文档来源模型中。</p>
 */
public interface PaperMetadataService {

    /**
     * 根据论文元数据补全文档来源信息。
     *
     * @param source 原始文档来源信息
     * @param paperMetadata 论文领域元数据
     * @return 补全后的文档来源信息
     */
    DocumentSource enrich(DocumentSource source, Map<String, Object> paperMetadata);
}
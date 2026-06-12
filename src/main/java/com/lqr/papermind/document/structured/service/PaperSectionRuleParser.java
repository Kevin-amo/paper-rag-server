package com.lqr.papermind.document.structured.service;

import com.lqr.papermind.document.service.DocumentPersistenceService;
import com.lqr.papermind.document.structured.model.StructuredParseResult;

/**
 * 基于章节标题规则的论文结构化解析器。
 */
public interface PaperSectionRuleParser {

    /**
     * 根据章节标题规则解析论文结构。
     *
     * @param document 文档详情
     * @return 规则解析结果
     */
    StructuredParseResult parse(DocumentPersistenceService.DocumentDetail document);
}
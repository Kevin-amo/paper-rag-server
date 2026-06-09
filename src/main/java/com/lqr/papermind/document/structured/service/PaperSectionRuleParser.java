package com.lqr.papermind.document.structured.service;

import com.lqr.papermind.document.service.DocumentPersistenceService;
import com.lqr.papermind.document.structured.model.StructuredParseResult;

/**
 * 基于章节标题规则的论文结构化解析器。
 */
public interface PaperSectionRuleParser {

    StructuredParseResult parse(DocumentPersistenceService.DocumentDetail document);
}
package com.lqr.paperragserver.document.structured.service;

import com.lqr.paperragserver.document.service.DocumentPersistenceService;
import com.lqr.paperragserver.document.structured.model.StructuredParseResult;

/**
 * 基于章节标题规则的论文结构化解析器。
 */
public interface PaperSectionRuleParser {

    StructuredParseResult parse(DocumentPersistenceService.DocumentDetail document);
}
package com.lqr.paperragserver.document.structured.service;

import com.lqr.paperragserver.document.service.DocumentPersistenceService;
import com.lqr.paperragserver.document.structured.model.ModelCompletionResult;
import com.lqr.paperragserver.document.structured.model.StructuredParseResult;

/**
 * 对规则解析结果做模型补全。
 */
public interface PaperStructuredModelCompleter {

    ModelCompletionResult complete(DocumentPersistenceService.DocumentDetail document, StructuredParseResult ruleResult);
}
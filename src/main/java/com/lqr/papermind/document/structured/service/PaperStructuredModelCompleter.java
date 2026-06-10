package com.lqr.papermind.document.structured.service;

import com.lqr.papermind.document.service.DocumentPersistenceService;
import com.lqr.papermind.document.structured.model.ModelCompletionResult;
import com.lqr.papermind.document.structured.model.StructuredParseResult;

/**
 * 对规则解析结果做模型补全。
 */
public interface PaperStructuredModelCompleter {

    ModelCompletionResult complete(DocumentPersistenceService.DocumentDetail document, StructuredParseResult ruleResult);
}
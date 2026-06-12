package com.lqr.papermind.document.structured.service;

import com.lqr.papermind.document.service.DocumentPersistenceService;
import com.lqr.papermind.document.structured.model.ModelCompletionResult;
import com.lqr.papermind.document.structured.model.StructuredParseResult;

/**
 * 对规则解析结果做模型补全。
 */
public interface PaperStructuredModelCompleter {

    /**
     * 使用大模型对规则解析结果进行补全。
     *
     * @param document   文档详情
     * @param ruleResult 规则解析结果
     * @return 模型补全结果
     */
    ModelCompletionResult complete(DocumentPersistenceService.DocumentDetail document, StructuredParseResult ruleResult);
}
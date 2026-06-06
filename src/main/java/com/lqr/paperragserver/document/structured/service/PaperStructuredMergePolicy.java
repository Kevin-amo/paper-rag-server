package com.lqr.paperragserver.document.structured.service;

import com.lqr.paperragserver.document.structured.model.StructuredParseResult;

/**
 * 合并规则解析和模型补全结果。
 */
public interface PaperStructuredMergePolicy {

    StructuredParseResult merge(StructuredParseResult ruleResult, StructuredParseResult modelResult);
}
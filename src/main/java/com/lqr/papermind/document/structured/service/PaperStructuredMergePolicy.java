package com.lqr.papermind.document.structured.service;

import com.lqr.papermind.document.structured.model.StructuredParseResult;

/**
 * 合并规则解析和模型补全结果。
 */
public interface PaperStructuredMergePolicy {

    /**
     * 将规则解析结果和模型补全结果进行合并。
     *
     * @param ruleResult 规则解析结果
     * @param modelResult 模型补全结果
     * @return 合并后的结构化解析结果
     */
    StructuredParseResult merge(StructuredParseResult ruleResult, StructuredParseResult modelResult);
}
package com.lqr.papermind.document.structured.service.impl;

import com.lqr.papermind.document.structured.model.PaperStructuredContent;
import com.lqr.papermind.document.structured.model.PaperStructuredContentSupport;
import com.lqr.papermind.document.structured.model.StructuredFieldEvidence;
import com.lqr.papermind.document.structured.model.StructuredParseResult;
import com.lqr.papermind.document.structured.service.PaperStructuredMergePolicy;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 规则优先、模型补齐的结构化结果合并策略。
 */
@Component
public class PaperStructuredMergePolicyImpl implements PaperStructuredMergePolicy {

    private static final double RULE_KEEP_CONFIDENCE = 0.7;

    @Override
    public StructuredParseResult merge(StructuredParseResult ruleResult, StructuredParseResult modelResult) {
        PaperStructuredContent content = ruleResult.content();
        Map<String, StructuredFieldEvidence> evidence = new LinkedHashMap<>();
        for (String field : PaperStructuredContentSupport.ALL_FIELDS) {
            StructuredFieldEvidence ruleEvidence = ruleResult.evidence().get(field);
            StructuredFieldEvidence modelEvidence = modelResult.evidence().get(field);
            Object ruleValue = PaperStructuredContentSupport.value(ruleResult.content(), field);
            Object modelValue = PaperStructuredContentSupport.value(modelResult.content(), field);
            // 规则结果非空且置信度 >= 0.7 时，保留规则结果
            boolean keepRule = !PaperStructuredContentSupport.isEmpty(ruleValue)
                    && ruleEvidence != null
                    && ruleEvidence.confidence() >= RULE_KEEP_CONFIDENCE;
            if (keepRule || PaperStructuredContentSupport.isEmpty(modelValue)) {
                // 保留规则结果
                evidence.put(field, mergedEvidence(field, ruleEvidence, PaperStructuredContentSupport.isEmpty(ruleValue)));
                continue;
            }
            // 模型结果非空时，使用模型结果
            content = PaperStructuredContentSupport.withValue(content, field, modelValue);
            evidence.put(field, mergedEvidence(field, modelEvidence, false));
        }
        List<String> missingFields = PaperStructuredContentSupport.emptyFields(content);
        List<String> lowConfidenceFields = evidence.values().stream()
                .filter(item -> !item.missing() && item.confidence() < RULE_KEEP_CONFIDENCE)
                .map(StructuredFieldEvidence::fieldName)
                .toList();
        return new StructuredParseResult(content, evidence, missingFields, lowConfidenceFields);
    }

    private StructuredFieldEvidence mergedEvidence(String field, StructuredFieldEvidence source, boolean missing) {
        if (source == null) {
            return new StructuredFieldEvidence(field, "MERGED", 0.0, true, null);
        }
        return new StructuredFieldEvidence(field, source.source(), source.confidence(), missing, source.evidence());
    }
}
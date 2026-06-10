package com.lqr.papermind.agent.paper;

import com.lqr.papermind.common.logging.LogSanitizer;
import com.lqr.papermind.common.model.AnswerCitation;
import com.lqr.papermind.rag.config.RagProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class CitationNormalizer {

    private final RagProperties ragProperties;

    /**
     * 对召回引用去重、排序并裁剪到目标数量。
     *
     * @param citations 原始引用列表
     * @param topK      本地检索片段数量配置
     * @return 归一化后的引用列表
     */
    public List<AnswerCitation> normalize(List<AnswerCitation> citations, Integer topK) {
        if (citations == null || citations.isEmpty()) {
            log.info("citation.normalize.done rawCount={} dedupCount={} citationLimit={} finalCount={} duplicateDroppedCount={} truncatedCount={}",
                    0, 0, citationLimit(topK), 0, 0, 0);
            return List.of();
        }
        int rawCount = citations.size();
        Map<String, AnswerCitation> citationByKey = new LinkedHashMap<>();
        int duplicateDroppedCount = 0;
        for (AnswerCitation citation : citations) {
            if (citation == null) {
                continue;
            }
            String key = citationKey(citation);
            AnswerCitation existing = citationByKey.get(key);
            if (existing == null || citation.rankScore() > existing.rankScore()) {
                if (existing != null) {
                    duplicateDroppedCount++;
                }
                citationByKey.put(key, citation);
            } else {
                duplicateDroppedCount++;
            }
        }
        int limit = citationLimit(topK);
        List<AnswerCitation> finalCitations = citationByKey.values().stream()
                .sorted((left, right) -> Double.compare(right.rankScore(), left.rankScore()))
                .limit(limit)
                .toList();
        int truncatedCount = Math.max(0, citationByKey.size() - finalCitations.size());
        log.info("citation.normalize.done rawCount={} dedupCount={} citationLimit={} finalCount={} duplicateDroppedCount={} truncatedCount={}",
                rawCount, citationByKey.size(), limit, finalCitations.size(), duplicateDroppedCount, truncatedCount);
        logDebugCitations(finalCitations);
        return finalCitations;
    }

    /**
     * 计算引用展示数量上限，优先使用本轮 topK 配置。
     *
     * @param topK 本地检索片段数量配置
     * @return 引用数量上限
     */
    private int citationLimit(Integer topK) {
        if (topK != null && topK > 0) {
            return topK;
        }
        return Math.max(1, ragProperties.defaultTopK());
    }

    /**
     * 在调试日志开启时输出最终引用的安全摘要。
     *
     * @param citations 最终引用列表
     */
    private void logDebugCitations(List<AnswerCitation> citations) {
        if (!log.isDebugEnabled()) {
            return;
        }
        for (AnswerCitation citation : citations) {
            log.debug("citation.final sourceId={} chunkId={} chunkIndex={} rankScore={} excerpt={}",
                    citation.sourceId(),
                    citation.chunkId(),
                    citation.chunkIndex(),
                    citation.rankScore(),
                    LogSanitizer.safeExcerpt(citation.excerpt(), 160));
        }
    }

    /**
     * 为引用生成去重键，优先使用片段标识，缺失时退回到来源和片段序号。
     *
     * @param citation 引用信息
     * @return 引用去重键
     */
    private String citationKey(AnswerCitation citation) {
        if (hasText(citation.chunkId())) {
            return "chunk:" + citation.chunkId();
        }
        return "source-chunk:" + nullToEmpty(citation.sourceId()) + ':' + citation.chunkIndex();
    }

    /**
     * 判断文本是否包含非空白内容。
     *
     * @param value 待判断文本
     * @return 是否存在有效文本
     */
    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * 将空值转换为空字符串，便于构建引用去重键。
     *
     * @param value 输入文本
     * @return 非空文本或空字符串
     */
    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
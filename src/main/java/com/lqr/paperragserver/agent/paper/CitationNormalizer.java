package com.lqr.paperragserver.agent.paper;

import com.lqr.paperragserver.common.logging.LogSanitizer;
import com.lqr.paperragserver.common.model.AnswerCitation;
import com.lqr.paperragserver.rag.config.RagProperties;
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

    private int citationLimit(Integer topK) {
        if (topK != null && topK > 0) {
            return topK;
        }
        return Math.max(1, ragProperties.defaultTopK());
    }

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

    private String citationKey(AnswerCitation citation) {
        if (hasText(citation.chunkId())) {
            return "chunk:" + citation.chunkId();
        }
        return "source-chunk:" + nullToEmpty(citation.sourceId()) + ':' + citation.chunkIndex();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
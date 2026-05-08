package com.lqr.paperragserver.rag;

import com.lqr.paperragserver.ai.LlmService;
import com.lqr.paperragserver.ai.PromptConstructionService;
import com.lqr.paperragserver.common.AnswerCitation;
import com.lqr.paperragserver.common.RagAnswer;
import com.lqr.paperragserver.common.RetrievedChunk;
import com.lqr.paperragserver.config.RagProperties;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 默认问答编排实现。
 */
@Service
public class DefaultRagAnswerService implements RagAnswerService {

    private final RagRetrievalService ragRetrievalService;
    private final PromptConstructionService promptConstructionService;
    private final LlmService llmService;
    private final RagProperties ragProperties;

    public DefaultRagAnswerService(RagRetrievalService ragRetrievalService,
                                   PromptConstructionService promptConstructionService,
                                   LlmService llmService,
                                   RagProperties ragProperties) {
        this.ragRetrievalService = ragRetrievalService;
        this.promptConstructionService = promptConstructionService;
        this.llmService = llmService;
        this.ragProperties = ragProperties;
    }

    @Override
    public RagAnswer answer(String question, Integer topK) {
        int resolvedTopK = topK == null || topK <= 0 ? ragProperties.defaultTopK() : topK;
        List<RetrievedChunk> chunks = ragRetrievalService.retrieve(question, resolvedTopK);
        PromptConstructionService.Prompt prompt = promptConstructionService.build(question, chunks);
        String answer = llmService.generate(prompt);
        List<AnswerCitation> citations = chunks.stream()
                .map(chunk -> new AnswerCitation(
                        chunk.chunk().sourceId(),
                        chunk.chunk().chunkId(),
                        chunk.chunk().chunkIndex(),
                        stringMetadata(chunk.chunk().metadata(), "title"),
                        cutExcerpt(chunk.chunk().content()),
                        chunk.score()))
                .toList();
        return new RagAnswer(answer, citations);
    }

    private String cutExcerpt(String content) {
        if (content == null) {
            return "";
        }
        int maxLength = 160;
        return content.length() <= maxLength ? content : content.substring(0, maxLength) + "...";
    }

    private String stringMetadata(Map<String, Object> metadata, String key) {
        if (metadata == null || !metadata.containsKey(key)) {
            return null;
        }
        Object value = metadata.get(key);
        return value == null ? null : String.valueOf(value);
    }
}
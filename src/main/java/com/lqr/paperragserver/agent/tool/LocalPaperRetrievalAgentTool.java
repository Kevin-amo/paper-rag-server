package com.lqr.paperragserver.agent.tool;

import com.lqr.paperragserver.agent.model.AgentToolResult;
import com.lqr.paperragserver.common.constant.MetadataKeys;
import com.lqr.paperragserver.common.model.AnswerCitation;
import com.lqr.paperragserver.common.model.RetrievedChunk;
import com.lqr.paperragserver.rag.config.RagProperties;
import com.lqr.paperragserver.rag.service.RagRetrievalService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 本地论文检索工具，负责从当前用户已入库的论文知识库中召回片段并生成引用信息。
 */
@Component
@RequiredArgsConstructor
public class LocalPaperRetrievalAgentTool implements AgentTool {

    private static final int MAX_EXCERPT_LENGTH = 220;

    private final RagRetrievalService ragRetrievalService;
    private final RagProperties ragProperties;

    @Override
    public String name() {
        return "local_paper_retrieval";
    }

    @Override
    public String description() {
        return "检索当前用户已上传并入库的本地论文知识库片段。";
    }

    @Override
    public AgentToolResult execute(UUID ownerUserId, Map<String, Object> input) {
        String query = stringValue(input.get("query"));
        int topK = intValue(input.get("topK"), ragProperties.defaultTopK());
        if (query.isBlank()) {
            return new AgentToolResult("本地检索跳过：query 为空。", "", List.of(), Map.of("localPaperChunks", List.of()));
        }
        List<RetrievedChunk> chunks = ragRetrievalService.retrieve(ownerUserId, query, topK);
        List<AnswerCitation> citations = buildCitations(chunks);
        List<Map<String, Object>> chunkMetadata = chunks.stream()
                .map(chunk -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("sourceId", chunk.chunk().sourceId());
                    item.put("chunkId", chunk.chunk().chunkId());
                    item.put("chunkIndex", chunk.chunk().chunkIndex());
                    item.put("title", stringMetadata(chunk.chunk().metadata(), MetadataKeys.TITLE));
                    item.put("rankScore", chunk.rankScore());
                    item.put("excerpt", cut(chunk.chunk().content(), MAX_EXCERPT_LENGTH));
                    return item;
                })
                .toList();
        String evidence = chunks.stream()
                .map(chunk -> "[本地论文] "
                        + firstNonBlank(stringMetadata(chunk.chunk().metadata(), MetadataKeys.TITLE), chunk.chunk().sourceId())
                        + " #" + chunk.chunk().chunkIndex()
                        + "\n" + cut(chunk.chunk().content(), 900))
                .reduce((left, right) -> left + "\n\n" + right)
                .orElse("未检索到本地论文片段。");
        Map<String, Object> metadata = Map.of("localPaperChunks", chunkMetadata);
        return new AgentToolResult("本地论文检索完成，找到 " + chunks.size() + " 个相关片段。", evidence, citations, metadata);
    }

    private List<AnswerCitation> buildCitations(List<RetrievedChunk> chunks) {
        return chunks.stream()
                .map(chunk -> new AnswerCitation(
                        chunk.chunk().sourceId(),
                        chunk.chunk().chunkId(),
                        chunk.chunk().chunkIndex(),
                        stringMetadata(chunk.chunk().metadata(), MetadataKeys.TITLE),
                        cut(chunk.chunk().content(), 160),
                        chunk.rankScore()))
                .toList();
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private int intValue(Object value, int fallback) {
        if (value instanceof Number number) {
            return Math.max(1, number.intValue());
        }
        if (value == null) {
            return fallback;
        }
        try {
            return Math.max(1, Integer.parseInt(String.valueOf(value)));
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private String stringMetadata(Map<String, Object> metadata, String key) {
        if (metadata == null || !metadata.containsKey(key)) {
            return null;
        }
        Object value = metadata.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private String firstNonBlank(String first, String second) {
        return first == null || first.isBlank() ? second : first;
    }

    private String cut(String content, int maxLength) {
        if (content == null) {
            return "";
        }
        String normalized = content.trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength) + "...";
    }
}
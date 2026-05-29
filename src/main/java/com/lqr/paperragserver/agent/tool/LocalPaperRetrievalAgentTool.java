package com.lqr.paperragserver.agent.tool;

import com.lqr.paperragserver.agent.model.AgentToolResult;
import com.lqr.paperragserver.common.constant.MetadataKeys;
import com.lqr.paperragserver.common.logging.LogSanitizer;
import com.lqr.paperragserver.common.model.AnswerCitation;
import com.lqr.paperragserver.common.model.RetrievedChunk;
import com.lqr.paperragserver.rag.config.RagProperties;
import com.lqr.paperragserver.rag.service.RagRetrievalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 本地论文检索工具，负责从当前用户已入库的论文知识库中召回片段并生成引用信息。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LocalPaperRetrievalAgentTool implements AgentTool {

    private static final int MAX_EXCERPT_LENGTH = 220;
    private static final Pattern NUMBERED_TITLE_PATTERN = Pattern.compile("^\\d+(?:\\.\\d+){0,4}(?:\\s*[.．、)]\\s*|\\s+)(?:abstract|introduction|related work|related works|methods?|experiments?|results?|discussion|conclusions?|references|bibliography)\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern CONTENTS_ENTRY_PATTERN = Pattern.compile("^.+?(?:\\.{2,}|…{2,}|\\s{2,}|\\t+)\\s*(?:\\d+|[ivxlcdm]+)\\s*$", Pattern.CASE_INSENSITIVE);

    private final RagRetrievalService ragRetrievalService;
    private final RagProperties ragProperties;

    /**
     * 返回本地论文检索工具的注册名称。
     *
     * @return 工具名称
     */
    @Override
    public String name() {
        return "local_paper_retrieval";
    }

    /**
     * 返回本地论文检索工具的能力说明，供规划器选择工具时使用。
     *
     * @return 工具能力描述
     */
    @Override
    public String description() {
        return "检索当前用户已上传并入库的本地论文知识库片段。";
    }

    /**
     * 执行本地论文知识库检索，并将召回片段整理为证据、引用和结构化元数据。
     *
     * @param ownerUserId 当前用户标识
     * @param input       规划器生成的检索参数
     * @return 本地检索工具结果
     */
    @Override
    public AgentToolResult execute(UUID ownerUserId, Map<String, Object> input) {
        long startNanos = System.nanoTime();
        String query = stringValue(input.get("query"));
        int topK = intValue(input.get("topK"), ragProperties.defaultTopK());
        log.info("agent.tool.local_paper.start ownerUserId={} queryExcerpt={} topK={}", ownerUserId, LogSanitizer.safeExcerpt(query, 160), topK);
        if (query.isBlank()) {
            log.warn("agent.tool.local_paper.skipped ownerUserId={} reason=EMPTY_QUERY", ownerUserId);
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
        log.info("agent.tool.local_paper.done ownerUserId={} queryExcerpt={} topK={} chunkCount={} citationCount={} metadataKeys={} costMs={}",
                ownerUserId, LogSanitizer.safeExcerpt(query, 160), topK, chunks.size(), citations.size(), metadata.keySet(), elapsedMs(startNanos));
        return new AgentToolResult("本地论文检索完成，找到 " + chunks.size() + " 个相关片段。", evidence, citations, metadata);
    }

    /**
     * 从召回片段中构建可展示引用，并过滤空内容、纯标题和目录项。
     *
     * @param chunks RAG 召回片段
     * @return 可展示的引用列表
     */
    private List<AnswerCitation> buildCitations(List<RetrievedChunk> chunks) {
        CitationFilterStats stats = new CitationFilterStats();
        List<AnswerCitation> citations = chunks.stream()
                .filter(chunk -> {
                    CitationFilterReason reason = citationFilterReason(chunk.chunk().content());
                    if (reason != CitationFilterReason.DISPLAYABLE) {
                        stats.increment(reason);
                        log.debug("citation.filter sourceId={} chunkId={} chunkIndex={} reason={} excerpt={}",
                                chunk.chunk().sourceId(), chunk.chunk().chunkId(), chunk.chunk().chunkIndex(), reason, LogSanitizer.safeExcerpt(chunk.chunk().content(), 160));
                        return false;
                    }
                    return true;
                })
                .map(chunk -> {
                    AnswerCitation citation = new AnswerCitation(
                            chunk.chunk().sourceId(),
                            chunk.chunk().chunkId(),
                            chunk.chunk().chunkIndex(),
                            stringMetadata(chunk.chunk().metadata(), MetadataKeys.TITLE),
                            cut(chunk.chunk().content(), 160),
                            chunk.rankScore());
                    log.debug("citation.build.item sourceId={} chunkIndex={} rankScore={} excerpt={}",
                            citation.sourceId(), citation.chunkIndex(), citation.rankScore(), LogSanitizer.safeExcerpt(citation.excerpt(), 160));
                    return citation;
                })
                .toList();
        log.info("citation.build.done rawCount={} finalCount={} emptyCount={} tooShortCount={} titleOnlyCount={} contentsEntryCount={}",
                chunks.size(), citations.size(), stats.emptyCount, stats.tooShortCount, stats.titleOnlyCount, stats.contentsEntryCount);
        return citations;
    }

    /**
     * 判断片段内容是否适合作为用户可见引用摘录。
     *
     * @param content 片段内容
     * @return 适合展示时返回 true
     */
    private boolean isDisplayableCitationExcerpt(String content) {
        return citationFilterReason(content) == CitationFilterReason.DISPLAYABLE;
    }

    /**
     * 识别引用摘录被过滤的原因，用于稳定统计和调试日志。
     *
     * @param content 片段内容
     * @return 引用过滤原因
     */
    private CitationFilterReason citationFilterReason(String content) {
        String normalized = normalizeWhitespace(content);
        if (normalized.isBlank()) {
            return CitationFilterReason.EMPTY;
        }
        if (normalized.length() <= 3) {
            return CitationFilterReason.TOO_SHORT;
        }
        String canonical = stripTrailingHeadingPunctuation(normalized).toLowerCase(Locale.ROOT);
        if (canonical.equals("abstract")
                || canonical.equals("摘要")
                || canonical.equals("introduction")
                || canonical.equals("related work")
                || canonical.equals("related works")
                || canonical.equals("methods")
                || canonical.equals("method")
                || canonical.equals("experiments")
                || canonical.equals("experiment")
                || canonical.equals("results")
                || canonical.equals("result")
                || canonical.equals("discussion")
                || canonical.equals("conclusion")
                || canonical.equals("conclusions")
                || canonical.equals("references")
                || canonical.equals("bibliography")) {
            return CitationFilterReason.TITLE_ONLY;
        }
        if (NUMBERED_TITLE_PATTERN.matcher(normalized).matches()) {
            return CitationFilterReason.TITLE_ONLY;
        }
        if (CONTENTS_ENTRY_PATTERN.matcher(normalized).matches()) {
            return CitationFilterReason.CONTENTS_ENTRY;
        }
        return CitationFilterReason.DISPLAYABLE;
    }

    /**
     * 归一化片段空白字符，便于后续标题和目录项判断。
     *
     * @param content 片段内容
     * @return 空白归一化后的文本
     */
    private String normalizeWhitespace(String content) {
        return LogSanitizer.normalizeWhitespace(content);
    }

    /**
     * 移除标题末尾的中英文冒号，降低纯标题判断的误差。
     *
     * @param content 片段内容
     * @return 去除末尾标题标点后的文本
     */
    private String stripTrailingHeadingPunctuation(String content) {
        String normalized = normalizeWhitespace(content);
        while (normalized.endsWith(":") || normalized.endsWith("：")) {
            normalized = normalized.substring(0, normalized.length() - 1).strip();
        }
        return normalized;
    }

    /**
     * 将任意输入值转换为去除首尾空白的字符串。
     *
     * @param value 输入值
     * @return 字符串值
     */
    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    /**
     * 将输入值转换为正整数，无法转换时使用兜底值。
     *
     * @param value    输入值
     * @param fallback 兜底数量
     * @return 至少为 1 的整数
     */
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

    /**
     * 从片段元数据中读取字符串字段。
     *
     * @param metadata 片段元数据
     * @param key      元数据键
     * @return 字符串字段值；不存在时返回 null
     */
    private String stringMetadata(Map<String, Object> metadata, String key) {
        if (metadata == null || !metadata.containsKey(key)) {
            return null;
        }
        Object value = metadata.get(key);
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 返回第一个非空白文本，常用于展示标题缺失时回退到来源标识。
     *
     * @param first  优先文本
     * @param second 兜底文本
     * @return 第一个有效文本
     */
    private String firstNonBlank(String first, String second) {
        return first == null || first.isBlank() ? second : first;
    }

    /**
     * 截断长文本并保留安全摘要，避免证据和引用摘录过长。
     *
     * @param content   待截断文本
     * @param maxLength 最大长度
     * @return 截断后的文本
     */
    private String cut(String content, int maxLength) {
        return LogSanitizer.safeExcerpt(content, maxLength);
    }

    /**
     * 将纳秒起点换算为毫秒耗时，用于日志记录。
     *
     * @param startNanos 起始纳秒时间
     * @return 已经过的毫秒数
     */
    private long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    /**
     * 引用摘录过滤原因，用于统计不可展示片段的类型。
     */
    private enum CitationFilterReason {
        DISPLAYABLE,
        EMPTY,
        TOO_SHORT,
        TITLE_ONLY,
        CONTENTS_ENTRY
    }

    /**
     * 引用过滤统计信息，记录各类不可展示片段的数量。
     */
    private static final class CitationFilterStats {
        private int emptyCount;
        private int tooShortCount;
        private int titleOnlyCount;
        private int contentsEntryCount;

        /**
         * 根据过滤原因累加对应计数，展示型片段不计入过滤统计。
         *
         * @param reason 过滤原因
         */
        private void increment(CitationFilterReason reason) {
            switch (reason) {
                case EMPTY -> emptyCount++;
                case TOO_SHORT -> tooShortCount++;
                case TITLE_ONLY -> titleOnlyCount++;
                case CONTENTS_ENTRY -> contentsEntryCount++;
                case DISPLAYABLE -> {
                }
            }
        }
    }
}
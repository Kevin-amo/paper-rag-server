package com.lqr.paperragserver.document.service.impl;

import com.lqr.paperragserver.common.model.DocumentChunk;
import com.lqr.paperragserver.common.model.DocumentSource;
import com.lqr.paperragserver.rag.config.RagProperties;
import com.lqr.paperragserver.common.constant.MetadataKeys;
import com.lqr.paperragserver.document.service.DocumentSplittingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 默认的文本切分实现。
 *
 * <p>按文档结构优先切分，再对超长结构块进行长度兜底切分，尽量保留标题、摘要、章节和参考文献等语义边界。</p>
 */
@Service
@RequiredArgsConstructor
public class DocumentSplittingServiceImpl implements DocumentSplittingService {

    private static final Pattern LINE_BREAK = Pattern.compile("\\R");
    private static final Pattern NUMBERED_HEADING = Pattern.compile("^(\\d+(?:\\.\\d+){0,4})(?:\\s*[.．、\\)]\\s*|\\s+)(.+?)\\s*$");
    private static final Pattern CHINESE_CHAPTER_HEADING = Pattern.compile("^第([一二三四五六七八九十百千0-9]+)([章节篇部分])\\s*(.*)$");
    private static final Pattern CHINESE_LIST_HEADING = Pattern.compile("^([一二三四五六七八九十]+)[、．.\\)]\\s*(.+)$");
    private static final Pattern CONTENTS_ENTRY_WITH_TRAILING_PAGE = Pattern.compile("^.+?(?:\\.{2,}|…{2,}|\\s{2,}|\\t+)\\s*(?:\\d+|[ivxlcdmIVXLCDM]+)\\s*$");
    private static final Pattern CONTENTS_NUMBERED_ENTRY = Pattern.compile("^(?:\\d+(?:\\.\\d+){0,4}|第[一二三四五六七八九十百千0-9]+[章节篇部分]|[一二三四五六七八九十]+[、．.\\)])\\s+.+?\\s+(?:\\d+|[ivxlcdmIVXLCDM]+)\\s*$");
    private static final Pattern CONTENTS_NUMBERED_ENTRY_WITH_ATTACHED_PAGE = Pattern.compile("^\\d+(?:\\.\\d+){0,4}\\s+.+?\\d+$");

    private static final Set<String> ABSTRACT_HEADINGS = Set.of("摘要", "abstract");
    private static final Set<String> CONTENTS_HEADINGS = Set.of("目录", "目次", "contents", "table of contents");
    private static final Set<String> CONCLUSION_HEADINGS = Set.of("结论", "总结", "conclusion", "conclusions", "concluding remarks");
    private static final Set<String> REFERENCES_HEADINGS = Set.of("参考文献", "references", "bibliography");
    private static final Set<String> STANDALONE_SECTION_HEADINGS = Set.of(
            "引言", "绪论", "前言", "introduction", "background", "related work", "related works",
            "方法", "method", "methods", "materials and methods",
            "实验", "experiment", "experiments", "results", "result",
            "讨论", "discussion", "analysis", "evaluation",
            "致谢", "acknowledgment", "acknowledgments", "acknowledgement", "acknowledgements"
    );

    private final RagProperties ragProperties;

    /**
     * 切分文章
     * @param source 文档来源信息
     * @param fullText 文档完整文本
     * @return 文章切分列表
     */
    @Override
    public List<DocumentChunk> split(DocumentSource source, String fullText) {
        Objects.requireNonNull(source, "source 不能为空");
        if (fullText == null || fullText.isBlank()) {
            return List.of();
        }

        List<ParagraphBlock> paragraphs = extractParagraphs(fullText);
        if (paragraphs.isEmpty()) {
            return List.of();
        }

        List<SectionBlock> sections = buildSections(paragraphs);
        List<DocumentChunk> chunks = new ArrayList<>();
        int chunkIndex = 0;
        for (SectionBlock section : sections) {
            for (ChunkSlice slice : splitSection(section)) {
                chunks.add(toDocumentChunk(source, chunkIndex++, slice));
            }
        }
        return chunks;
    }

    /**
     * 按空白行分成多个段落
     * @param fullText 文档完整文本
     * @return 段落分片列表
     */
    private List<ParagraphBlock> extractParagraphs(String fullText) {
        List<ParagraphBlock> paragraphs = new ArrayList<>();
        List<LineBlock> lines = extractLines(fullText);
        int paragraphStart = -1;
        int paragraphEnd = -1;
        for (LineBlock line : lines) {
            if (line.content().isBlank()) {
                if (paragraphStart >= 0) {
                    appendParagraphBlock(paragraphs, fullText, paragraphStart, paragraphEnd);
                    paragraphStart = -1;
                    paragraphEnd = -1;
                }
                continue;
            }
            if (paragraphStart < 0) {
                paragraphStart = line.start();
            }
            paragraphEnd = line.end();
        }
        if (paragraphStart >= 0) {
            appendParagraphBlock(paragraphs, fullText, paragraphStart, paragraphEnd);
        }
        return paragraphs;
    }

    /**
     * 在段落列表中追加当前截取出的非空段落块。
     *
     * @param paragraphs 段落结果列表
     * @param fullText 原始全文
     * @param start 段落起始偏移
     * @param end 段落结束偏移
     */
    private void appendParagraphBlock(List<ParagraphBlock> paragraphs, String fullText, int start, int end) {
        String paragraphText = fullText.substring(start, end).strip();
        if (paragraphText.isBlank()) {
            return;
        }

        ParagraphBlock paragraph = new ParagraphBlock(paragraphText, start, end);
        paragraphs.addAll(splitLeadingHeadingLine(paragraph));
    }

    /**
     * 拆分“标题首行 + 正文 ”的复合段落，避免标题与内容粘连。
     *
     * @param paragraph 原始段落块
     * @return 拆分后的段落列表
     */
    private List<ParagraphBlock> splitLeadingHeadingLine(ParagraphBlock paragraph) {
        Matcher matcher = LINE_BREAK.matcher(paragraph.content());
        if (!matcher.find()) {
            return List.of(paragraph);
        }

        String firstLine = paragraph.content().substring(0, matcher.start()).strip();
        String remainder = paragraph.content().substring(matcher.end()).strip();
        if (firstLine.isBlank() || remainder.isBlank()) {
            return List.of(paragraph);
        }
        if (!isExplicitHeading(normalizeWhitespace(firstLine))) {
            return List.of(paragraph);
        }

        int headingEnd = paragraph.start() + matcher.start();
        int remainderStart = paragraph.start() + matcher.end();
        return List.of(
                new ParagraphBlock(firstLine, paragraph.start(), headingEnd),
                new ParagraphBlock(remainder, remainderStart, paragraph.end())
        );
    }

    /**
     * 判断文本是否满足显式标题的识别条件。
     *
     * @param text 待判断文本
     * @return 命中任一标题规则时返回 true
     */
    private boolean isExplicitHeading(String text) {
        return detectNumberedHeading(text).isPresent()
                || detectKeywordHeading(text).isPresent()
                || detectStandaloneHeading(text).isPresent();
    }

    /**
     * 把整段文本按换行符拆分，并记录每一行在原始字符串里的起止位置
     * @param fullText 文档完整文本
     * @return 截断结果列表
     */
    private List<LineBlock> extractLines(String fullText) {
        List<LineBlock> lines = new ArrayList<>();
        Matcher matcher = LINE_BREAK.matcher(fullText);
        int start = 0;
        // 找到换行符
        while (matcher.find()) {
            // 每找到一个换行符就截出一行
            int end = matcher.start();
            lines.add(new LineBlock(fullText.substring(start, end), start, end));
            start = matcher.end();
        }
        lines.add(new LineBlock(fullText.substring(start), start, fullText.length()));
        return lines;
    }

    /**
     * 根据段落序列构建带层级信息的章节块。
     *
     * @param paragraphs 已抽取的段落列表
     * @return 按章节组织后的结构块列表
     */
    private List<SectionBlock> buildSections(List<ParagraphBlock> paragraphs) {
        List<SectionBlock> sections = new ArrayList<>();
        SectionBuilder current = null;
        boolean seenAnyContent = false;

        for (int index = 0; index < paragraphs.size(); index++) {
            ParagraphBlock paragraph = paragraphs.get(index);
            if (current == null && startsImplicitContentsBlock(paragraphs, index)) {
                current = new SectionBuilder("目录", SectionType.CONTENTS, 1);
                current.add(paragraph);
                seenAnyContent = true;
                continue;
            }
            if (current != null && current.type() == SectionType.CONTENTS && containsContentsEntries(paragraph)) {
                current.add(paragraph);
                seenAnyContent = true;
                continue;
            }

            if (current != null && current.type() == SectionType.TITLE && startsImplicitContentsBlock(paragraphs, index)) {
                sections.add(current.build());
                current = new SectionBuilder("目录", SectionType.CONTENTS, 1);
                current.add(paragraph);
                seenAnyContent = true;
                continue;
            }

            Optional<DetectedHeading> heading = detectHeading(paragraph, !seenAnyContent);
            seenAnyContent = true;

            if (heading.isPresent()) {
                if (current != null) {
                    sections.add(current.build());
                }
                DetectedHeading detected = heading.get();
                current = new SectionBuilder(detected.title(), detected.type(), detected.level());
                current.add(paragraph);
                continue;
            }

            if (current == null) {
                current = new SectionBuilder("前置内容", SectionType.PREFACE, 0);
                current.add(paragraph);
                continue;
            }

            if (current.type() == SectionType.TITLE) {
                sections.add(current.build());
                current = new SectionBuilder("前置内容", SectionType.PREFACE, 0);
            }
            current.add(paragraph);
        }

        if (current != null) {
            sections.add(current.build());
        }
        return sections;
    }

    /**
     * 识别段落是否可作为新的章节起点，按“编号标题 → 关键字标题 → 独立短标题 → 首段题名”顺序判断。
     *
     * @param paragraph 当前段落
     * @param firstContentParagraph 是否为全文首个非空内容段落
     * @return 识别出的标题信息；未命中时为空
     */
    private Optional<DetectedHeading> detectHeading(ParagraphBlock paragraph, boolean firstContentParagraph) {
        String normalized = normalizeWhitespace(paragraph.content());
        if (normalized.isBlank()) {
            return Optional.empty();
        }

        Optional<DetectedHeading> numberedHeading = detectNumberedHeading(normalized);
        if (numberedHeading.isPresent()) {
            return numberedHeading;
        }

        Optional<DetectedHeading> keywordHeading = detectKeywordHeading(normalized);
        if (keywordHeading.isPresent()) {
            return keywordHeading;
        }

        Optional<DetectedHeading> standaloneHeading = detectStandaloneHeading(normalized);
        if (standaloneHeading.isPresent()) {
            return standaloneHeading;
        }

        if (firstContentParagraph && looksLikeTitle(paragraph.content())) {
            return Optional.of(new DetectedHeading(normalized, SectionType.TITLE, 0));
        }

        return Optional.empty();
    }

    /**
     * 识别常见的编号式标题，包括阿拉伯数字层级标题和中文章节/列表标题。
     *
     * @param text 归一化后的单段文本
     * @return 命中时返回标题类型与层级
     */
    private Optional<DetectedHeading> detectNumberedHeading(String text) {
        Matcher numbered = NUMBERED_HEADING.matcher(text);
        if (numbered.matches()) {
            int level = numbered.group(1).split("\\.").length;
            return Optional.of(new DetectedHeading(text, SectionType.SECTION, level));
        }

        Matcher chineseChapter = CHINESE_CHAPTER_HEADING.matcher(text);
        if (chineseChapter.matches()) {
            return Optional.of(new DetectedHeading(text, SectionType.SECTION, 1));
        }

        Matcher chineseList = CHINESE_LIST_HEADING.matcher(text);
        if (chineseList.matches()) {
            return Optional.of(new DetectedHeading(text, SectionType.SECTION, 1));
        }

        return Optional.empty();
    }

    /**
     * 识别摘要、结论、参考文献等具有固定语义的关键字标题。
     *
     * @param text 归一化后的单段文本
     * @return 命中时返回对应章节类型
     */
    private Optional<DetectedHeading> detectKeywordHeading(String text) {
        String abstractLabel = matchHeadingLabel(text, ABSTRACT_HEADINGS);
        if (abstractLabel != null) {
            return Optional.of(new DetectedHeading(abstractLabel, SectionType.ABSTRACT, 1));
        }

        String contentsLabel = matchHeadingLabel(text, CONTENTS_HEADINGS);
        if (contentsLabel != null) {
            return Optional.of(new DetectedHeading(contentsLabel, SectionType.CONTENTS, 1));
        }

        String conclusionLabel = matchHeadingLabel(text, CONCLUSION_HEADINGS);
        if (conclusionLabel != null) {
            return Optional.of(new DetectedHeading(conclusionLabel, SectionType.CONCLUSION, 1));
        }

        String referencesLabel = matchHeadingLabel(text, REFERENCES_HEADINGS);
        if (referencesLabel != null) {
            return Optional.of(new DetectedHeading(referencesLabel, SectionType.REFERENCES, 1));
        }
        return Optional.empty();
    }

    /**
     * 识别没有编号、但名称本身足够典型的独立章节标题。
     *
     * @param text 归一化后的单段文本
     * @return 命中时返回通用章节信息
     */
    private Optional<DetectedHeading> detectStandaloneHeading(String text) {
        String lower = stripTrailingHeadingPunctuation(text).toLowerCase(Locale.ROOT);
        if (STANDALONE_SECTION_HEADINGS.contains(lower)) {
            return Optional.of(new DetectedHeading(stripTrailingHeadingPunctuation(text), SectionType.SECTION, 1));
        }
        return Optional.empty();
    }

    /**
     * 从标题文本中提取真正的标题标签，兼容“摘要：”“References:”这类前缀写法。
     *
     * @param originalText 原始标题文本
     * @param keywords 可接受的标题关键字集合
     * @return 命中的标题标签；未命中时返回 null
     */
    private String matchHeadingLabel(String originalText, Set<String> keywords) {
        String stripped = stripTrailingHeadingPunctuation(originalText);
        String strippedLower = stripped.toLowerCase(Locale.ROOT);
        if (keywords.contains(strippedLower)) {
            return stripped;
        }

        int asciiColon = originalText.indexOf(':');
        int chineseColon = originalText.indexOf('：');
        int colonIndex;
        if (asciiColon < 0) {
            colonIndex = chineseColon;
        } else if (chineseColon < 0) {
            colonIndex = asciiColon;
        } else {
            colonIndex = Math.min(asciiColon, chineseColon);
        }
        if (colonIndex < 0) {
            return null;
        }

        String headingLabel = originalText.substring(0, colonIndex).strip();
        if (keywords.contains(headingLabel.toLowerCase(Locale.ROOT))) {
            return headingLabel;
        }
        return null;
    }

    /**
     * 去掉标题尾部常见的冒号，避免“摘要:”与“摘要”被识别成两个标签。
     *
     * @param text 原始文本
     * @return 清理后的标题文本
     */
    private String stripTrailingHeadingPunctuation(String text) {
        String normalized = normalizeWhitespace(text);
        if (normalized.endsWith(":") || normalized.endsWith("：")) {
            return normalized.substring(0, normalized.length() - 1).strip();
        }
        return normalized;
    }

    /**
     * 用较保守的启发式规则识别论文主标题，避免把正文短句误判为题名。
     *
     * @param text 原始段落文本
     * @return 符合题名特征时返回 true
     */
    private boolean looksLikeTitle(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }

        String normalized = normalizeWhitespace(text);
        if (normalized.length() > 180) {
            return false;
        }
        if (text.contains("\n") || text.contains("\r")) {
            return false;
        }
        if (NUMBERED_HEADING.matcher(normalized).matches()) {
            return false;
        }
        if (CHINESE_CHAPTER_HEADING.matcher(normalized).matches() || CHINESE_LIST_HEADING.matcher(normalized).matches()) {
            return false;
        }
        String lower = stripTrailingHeadingPunctuation(normalized).toLowerCase(Locale.ROOT);
        if (ABSTRACT_HEADINGS.contains(lower)
                || CONTENTS_HEADINGS.contains(lower)
                || CONCLUSION_HEADINGS.contains(lower)
                || REFERENCES_HEADINGS.contains(lower)) {
            return false;
        }
        if (STANDALONE_SECTION_HEADINGS.contains(lower)) {
            return false;
        }
        if (normalized.endsWith("。") || normalized.endsWith(".") || normalized.endsWith("!") || normalized.endsWith("?")) {
            return false;
        }
        long wordCount = normalized.chars().filter(Character::isWhitespace).count() + 1;
        return wordCount <= 18;
    }

    /**
     * 以结构边界聚合章节内容；目录章节优先按目录项切分，其余章节仍按段落边界聚合。
     *
     * @param section 章节块
     * @return 当前章节生成的切片列表
     */
    private List<ChunkSlice> splitSection(SectionBlock section) {
        if (section.type() == SectionType.CONTENTS) {
            return section.paragraphs().isEmpty() ? List.of() : List.of(toChunkSlice(section, section.paragraphs()));
        }
        return splitParagraphUnits(section, section.paragraphs());
    }

    /**
     * 按段落边界聚合切片，超长段落回退为滑动窗口切分。
     */
    private List<ChunkSlice> splitParagraphUnits(SectionBlock section, List<ParagraphBlock> paragraphs) {
        List<ChunkSlice> chunks = new ArrayList<>();
        List<ParagraphBlock> buffer = new ArrayList<>();
        int bufferedLength = 0;
        int chunkSize = ragProperties.chunkSize();

        for (ParagraphBlock paragraph : paragraphs) {
            String content = paragraph.content();
            if (content.isBlank()) {
                continue;
            }

            if (content.length() > chunkSize) {
                if (!buffer.isEmpty()) {
                    chunks.add(toChunkSlice(section, buffer));
                    buffer.clear();
                    bufferedLength = 0;
                }
                chunks.addAll(splitLongParagraph(section, paragraph));
                continue;
            }

            if (buffer.isEmpty()) {
                buffer.add(paragraph);
                bufferedLength = content.length();
                continue;
            }

            int candidateLength = bufferedLength + 2 + content.length();
            if (candidateLength > chunkSize) {
                chunks.add(toChunkSlice(section, buffer));
                buffer.clear();
                buffer.add(paragraph);
                bufferedLength = content.length();
                continue;
            }

            buffer.add(paragraph);
            bufferedLength = candidateLength;
        }

        if (!buffer.isEmpty()) {
            chunks.add(toChunkSlice(section, buffer));
        }
        return chunks;
    }

    /**
     * 判断当前位置是否以隐式目录条目块开头。
     */
    private boolean startsImplicitContentsBlock(List<ParagraphBlock> paragraphs, int startIndex) {
        int matchedEntries = 0;
        for (int index = startIndex; index < paragraphs.size() && index < startIndex + 12; index++) {
            ParagraphBlock paragraph = paragraphs.get(index);
            int paragraphEntries = countContentsEntryLines(paragraph);
            if (paragraphEntries == 0) {
                break;
            }
            matchedEntries += paragraphEntries;
            if (matchedEntries >= 3) {
                return true;
            }
        }
        return false;
    }

    /**
     * 统计段落中符合目录项特征的行数。
     */
    private int countContentsEntryLines(ParagraphBlock paragraph) {
        int count = 0;
        for (LineBlock line : extractLines(paragraph.content())) {
            if (looksLikeContentsEntry(line.content())) {
                count++;
            }
        }
        return count;
    }

    /**
     * 判断段落是否包含目录项行。
     */
    private boolean containsContentsEntries(ParagraphBlock paragraph) {
        for (LineBlock line : extractLines(paragraph.content())) {
            if (looksLikeContentsEntry(line.content())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断单行文本是否符合目录项格式。
     */
    private boolean looksLikeContentsEntry(String text) {
        String normalized = normalizeWhitespace(text);
        if (normalized.isBlank() || normalized.length() > 240) {
            return false;
        }

        String lower = stripTrailingHeadingPunctuation(normalized).toLowerCase(Locale.ROOT);
        if (CONTENTS_HEADINGS.contains(lower)) {
            return false;
        }
        return CONTENTS_ENTRY_WITH_TRAILING_PAGE.matcher(normalized).matches()
                || CONTENTS_NUMBERED_ENTRY.matcher(normalized).matches()
                || CONTENTS_NUMBERED_ENTRY_WITH_ATTACHED_PAGE.matcher(normalized).matches();
    }

    /**
     * 对无法按段落直接容纳的超长段落执行重叠切分，降低句子被截断后的语义损失。
     *
     * @param section 所属章节
     * @param paragraph 超长段落
     * @return 切分后的连续切片列表
     */
    private List<ChunkSlice> splitLongParagraph(SectionBlock section, ParagraphBlock paragraph) {
        List<ChunkSlice> chunks = new ArrayList<>();
        String content = paragraph.content();
        int chunkSize = ragProperties.chunkSize();
        int overlap = Math.min(ragProperties.chunkOverlap(), Math.max(0, chunkSize - 1));

        int start = 0;
        while (start < content.length()) {
            int end = Math.min(content.length(), start + chunkSize);
            String chunkContent = content.substring(start, end).strip();
            if (!chunkContent.isBlank()) {
                chunks.add(new ChunkSlice(
                        chunkContent,
                        paragraph.start() + start,
                        paragraph.start() + end,
                        section.title(),
                        section.type(),
                        section.level()
                ));
            }
            if (end >= content.length()) {
                break;
            }
            start = Math.max(end - overlap, start + 1);
        }
        return chunks;
    }

    /**
     * 把一组连续段落封装为单个切片，并保留它们在原文中的跨度。
     *
     * @param section 所属章节
     * @param paragraphs 连续段落列表
     * @return 聚合后的切片对象
     */
    private ChunkSlice toChunkSlice(SectionBlock section, List<ParagraphBlock> paragraphs) {
        String content = paragraphs.stream()
                .map(ParagraphBlock::content)
                .collect(Collectors.joining("\n\n"));
        int start = paragraphs.get(0).start();
        int end = paragraphs.get(paragraphs.size() - 1).end();
        return new ChunkSlice(content, start, end, section.title(), section.type(), section.level());
    }

    /**
     * 组装对外暴露的文档块对象，并补齐来源、章节和原文偏移等元数据。
     *
     * @param source 文档来源
     * @param chunkIndex 当前切片序号
     * @param slice 内部切片对象
     * @return 可持久化或可嵌入的文档块
     */
    private DocumentChunk toDocumentChunk(DocumentSource source, int chunkIndex, ChunkSlice slice) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (source.metadata() != null) {
            source.metadata().forEach((key, value) -> {
                if (!MetadataKeys.DOCUMENT_ASSETS.equals(key)) {
                    metadata.put(key, value);
                }
            });
        }
        List<String> assetIds = overlappingAssetIds(source.metadata(), slice.start(), slice.end());
        if (!assetIds.isEmpty()) {
            metadata.put(MetadataKeys.ASSET_IDS, assetIds);
        }
        metadata.put(MetadataKeys.SECTION_TITLE, slice.sectionTitle());
        metadata.put(MetadataKeys.SECTION_TYPE, slice.sectionType().name());
        metadata.put(MetadataKeys.SECTION_LEVEL, slice.sectionLevel());
        metadata.put(MetadataKeys.CHUNK_START, slice.start());
        metadata.put(MetadataKeys.CHUNK_END, slice.end());
        metadata.put(MetadataKeys.CHUNK_LENGTH, slice.content().length());
        return new DocumentChunk(
                UUID.nameUUIDFromBytes((source.sourceId() + "::" + chunkIndex + "::" + slice.start() + "::" + slice.end()).getBytes()).toString(),
                source.sourceId(),
                chunkIndex,
                slice.content(),
                metadata
        );
    }

    /**
     * 查找与当前切片原文范围重叠的文档资产 ID。
     */
    private List<String> overlappingAssetIds(Map<String, Object> sourceMetadata, int chunkStart, int chunkEnd) {
        Object documentAssets = sourceMetadata == null ? null : sourceMetadata.get(MetadataKeys.DOCUMENT_ASSETS);
        if (!(documentAssets instanceof List<?> assets)) {
            return List.of();
        }
        return assets.stream()
                .filter(Map.class::isInstance)
                .map(asset -> assetIdIfOverlapping((Map<?, ?>) asset, chunkStart, chunkEnd))
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * 若资产文本范围与切片范围重叠，则返回资产 ID。
     */
    private String assetIdIfOverlapping(Map<?, ?> asset, int chunkStart, int chunkEnd) {
        Integer textStart = integerValue(asset.get(MetadataKeys.TEXT_START));
        Integer textEnd = integerValue(asset.get(MetadataKeys.TEXT_END));
        Object assetId = asset.get(MetadataKeys.ASSET_ID);
        if (assetId == null || textStart == null || textEnd == null) {
            return null;
        }
        return textStart <= chunkEnd && textEnd >= chunkStart ? String.valueOf(assetId) : null;
    }

    /**
     * 将元数据值安全转换为整数。
     */
    private Integer integerValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * 归一化空白字符，便于标题识别和长度判断在不同换行/空格形式下保持一致。
     *
     * @param text 原始文本
     * @return 压缩空白后的文本；入参为 null 时返回空串
     */
    private String normalizeWhitespace(String text) {
        return text == null ? "" : text.replaceAll("\\s+", " ").strip();
    }

    private record LineBlock(String content, int start, int end) {
    }

    private record ParagraphBlock(String content, int start, int end) {
    }

    private record ChunkSlice(String content, int start, int end, String sectionTitle, SectionType sectionType, int sectionLevel) {
    }

    private record DetectedHeading(String title, SectionType type, int level) {
    }

    private record SectionBlock(String title, SectionType type, int level, List<ParagraphBlock> paragraphs) {
    }

    private static final class SectionBuilder {
        private final String title;
        private final SectionType type;
        private final int level;
        private final List<ParagraphBlock> paragraphs = new ArrayList<>();

        /**
         * 创建章节构建器。
         */
        private SectionBuilder(String title, SectionType type, int level) {
            this.title = title;
            this.type = type;
            this.level = level;
        }

        /**
         * 追加章节内段落。
         */
        private void add(ParagraphBlock paragraph) {
            paragraphs.add(paragraph);
        }

        /**
         * 构建不可变章节块。
         */
        private SectionBlock build() {
            return new SectionBlock(title, type, level, List.copyOf(paragraphs));
        }

        /**
         * 返回当前章节类型。
         */
        private SectionType type() {
            return type;
        }
    }

    private enum SectionType {
        TITLE,
        PREFACE,
        ABSTRACT,
        CONTENTS,
        SECTION,
        CONCLUSION,
        REFERENCES
    }
}
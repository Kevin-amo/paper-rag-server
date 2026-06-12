package com.lqr.papermind.document.service.impl;

import com.lqr.papermind.common.logging.LogSanitizer;
import com.lqr.papermind.common.model.DocumentChunk;
import com.lqr.papermind.common.model.DocumentSource;
import com.lqr.papermind.rag.config.RagProperties;
import com.lqr.papermind.common.constant.MetadataKeys;
import com.lqr.papermind.document.service.DocumentSplittingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
@Slf4j
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
    private static final int VISUAL_ARTIFACT_RUN_MIN_LINES = 4;
    private static final int FIGURE_CONTEXT_MIN_LENGTH = 80;
    private static final int CONTEXT_PARAGRAPH_MIN_LENGTH = 20;

    private static final String CHUNK_TYPE_TEXT = "TEXT";
    private static final String CHUNK_TYPE_FIGURE_CONTEXT = "FIGURE_CONTEXT";
    private static final String RAW_ASSET_TYPE = "type";
    private static final String RAW_CAPTION = "caption";
    private static final String RAW_CAPTION_TEXT = "captionText";
    private static final String RAW_EXTRACTED_TEXT = "extractedText";
    private static final String RAW_PAGE = "page";
    private static final String RAW_BBOX = "bbox";
    private static final String RAW_BOUNDING_BOX = "boundingBox";
    private static final String RAW_START = "start";
    private static final String RAW_END = "end";

    private static final Set<String> FIGURE_ASSET_TYPES = Set.of("figure", "image", "table");

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
        long startNanos = System.nanoTime();
        log.info("document.split.start sourceId={} textLength={} chunkSize={} chunkOverlap={}",
                source.sourceId(), textLength(fullText), ragProperties.chunkSize(), ragProperties.chunkOverlap());
        if (fullText == null || fullText.isBlank()) {
            log.info("document.split.chunks.done sourceId={} textLength={} paragraphCount={} sectionCount={} chunkCount={} skippedEmptyCount={} skippedTitleOnlyCount={} skippedTooShortCount={} costMs={}",
                    source.sourceId(), textLength(fullText), 0, 0, 0, 1, 0, 0, elapsedMs(startNanos));
            return List.of();
        }

        List<ParagraphBlock> paragraphs = extractParagraphs(fullText);
        log.info("document.split.paragraphs.done sourceId={} textLength={} paragraphCount={} costMs={}",
                source.sourceId(), fullText.length(), paragraphs.size(), elapsedMs(startNanos));
        if (paragraphs.isEmpty()) {
            log.info("document.split.chunks.done sourceId={} textLength={} paragraphCount={} sectionCount={} chunkCount={} skippedEmptyCount={} skippedTitleOnlyCount={} skippedTooShortCount={} costMs={}",
                    source.sourceId(), fullText.length(), 0, 0, 0, 1, 0, 0, elapsedMs(startNanos));
            return List.of();
        }

        List<SectionBlock> sections = buildSections(paragraphs);
        log.info("document.split.sections.done sourceId={} paragraphCount={} sectionCount={} costMs={}",
                source.sourceId(), paragraphs.size(), sections.size(), elapsedMs(startNanos));
        logSections(source.sourceId(), sections);
        List<DocumentChunk> textChunks = new ArrayList<>();
        SplitSkipStats skipStats = new SplitSkipStats();
        int chunkIndex = 0;
        for (SectionBlock section : sections) {
            for (ChunkSlice slice : splitSection(section)) {
                ChunkSlice cleanedSlice = removeVisualArtifactLineRuns(slice);
                String normalized = LogSanitizer.normalizeWhitespace(cleanedSlice.content());
                if (normalized.isBlank()) {
                    skipStats.emptyCount++;
                    continue;
                }
                if (normalized.length() <= 3) {
                    skipStats.tooShortCount++;
                    continue;
                }
                if (isTitleOnlyChunk(cleanedSlice)) {
                    skipStats.titleOnlyCount++;
                    continue;
                }
                DocumentChunk chunk = toDocumentChunk(source, chunkIndex++, cleanedSlice);
                textChunks.add(chunk);
                log.debug("document.split.chunk sourceId={} chunkId={} chunkIndex={} sectionTitle={} sectionType={} chunkLength={} excerpt={}",
                        source.sourceId(), chunk.chunkId(), chunk.chunkIndex(), cleanedSlice.sectionTitle(), cleanedSlice.sectionType(), cleanedSlice.content().length(), LogSanitizer.safeExcerpt(cleanedSlice.content(), 160));
            }
        }
        List<DocumentChunk> figureContextChunks = buildFigureContextChunks(source, fullText, paragraphs, sections);
        List<DocumentChunk> chunks = mergeAndReindexChunks(textChunks, figureContextChunks);
        log.info("document.split.figure-context.done sourceId={} figureContextChunkCount={}",
                source.sourceId(), figureContextChunks.size());
        log.info("document.split.chunks.done sourceId={} textLength={} paragraphCount={} sectionCount={} textChunkCount={} figureContextChunkCount={} chunkCount={} skippedEmptyCount={} skippedTitleOnlyCount={} skippedTooShortCount={} costMs={}",
                source.sourceId(), fullText.length(), paragraphs.size(), sections.size(), textChunks.size(), figureContextChunks.size(), chunks.size(), skipStats.emptyCount, skipStats.titleOnlyCount, skipStats.tooShortCount, elapsedMs(startNanos));
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
            return section.paragraphs().isEmpty() ? List.of() : List.of(toChunkSlice(section, null, section.paragraphs()));
        }
        return splitParagraphUnits(section, section.paragraphs());
    }

    /**
     * 按段落边界聚合切片，超长段落回退为滑动窗口切分。
     */
    private List<ChunkSlice> splitParagraphUnits(SectionBlock section, List<ParagraphBlock> paragraphs) {
        List<ChunkSlice> chunks = new ArrayList<>();
        if (paragraphs.isEmpty()) {
            return chunks;
        }

        ParagraphBlock leadingHeading = standaloneSectionHeading(section, paragraphs.get(0)) ? paragraphs.get(0) : null;
        List<ParagraphBlock> bodyParagraphs = leadingHeading == null ? paragraphs : paragraphs.subList(1, paragraphs.size());
        if (bodyParagraphs.isEmpty()) {
            return chunks;
        }

        List<ParagraphBlock> buffer = new ArrayList<>();
        int bufferedLength = 0;
        int chunkSize = ragProperties.chunkSize();
        boolean firstChunk = true;

        for (ParagraphBlock paragraph : bodyParagraphs) {
            String content = paragraph.content();
            if (content.isBlank()) {
                continue;
            }

            int leadingHeadingLength = firstChunk && leadingHeading != null ? leadingHeading.content().length() + 2 : 0;
            if (leadingHeadingLength + content.length() > chunkSize) {
                if (!buffer.isEmpty()) {
                    chunks.add(toChunkSlice(section, leadingHeadingFor(firstChunk, leadingHeading), buffer));
                    buffer.clear();
                    bufferedLength = 0;
                    firstChunk = false;
                }
                chunks.addAll(splitLongParagraph(section, paragraph, leadingHeadingFor(firstChunk, leadingHeading)));
                firstChunk = false;
                continue;
            }

            if (content.length() > chunkSize) {
                if (!buffer.isEmpty()) {
                    chunks.add(toChunkSlice(section, leadingHeadingFor(firstChunk, leadingHeading), buffer));
                    buffer.clear();
                    bufferedLength = 0;
                    firstChunk = false;
                }
                chunks.addAll(splitLongParagraph(section, paragraph, leadingHeadingFor(firstChunk, leadingHeading)));
                firstChunk = false;
                continue;
            }

            if (buffer.isEmpty()) {
                buffer.add(paragraph);
                bufferedLength = content.length();
                continue;
            }

            int bodyCandidateLength = bufferedLength + 2 + content.length();
            int candidateLength = bodyCandidateLength;
            if (firstChunk && leadingHeading != null) {
                candidateLength += leadingHeading.content().length() + 2;
            }
            if (candidateLength > chunkSize) {
                chunks.add(toChunkSlice(section, leadingHeadingFor(firstChunk, leadingHeading), buffer));
                buffer.clear();
                buffer.add(paragraph);
                bufferedLength = content.length();
                firstChunk = false;
                continue;
            }

            buffer.add(paragraph);
            bufferedLength = bodyCandidateLength;
        }

        if (!buffer.isEmpty()) {
            chunks.add(toChunkSlice(section, leadingHeadingFor(firstChunk, leadingHeading), buffer));
        }
        return chunks;
    }

    /**
     * 返回首个切片的标题段落，非首个切片返回 null。
     *
     * @param firstChunk 是否为首个切片
     * @param leadingHeading 标题段落
     * @return 标题段落或 null
     */
    private ParagraphBlock leadingHeadingFor(boolean firstChunk, ParagraphBlock leadingHeading) {
        return firstChunk ? leadingHeading : null;
    }

    /**
     * 判断段落是否为章节的独立标题行。
     *
     * @param section 所属章节
     * @param paragraph 待判断段落
     * @return 是独立标题行时返回 true
     */
    private boolean standaloneSectionHeading(SectionBlock section, ParagraphBlock paragraph) {
        if (section.type() == SectionType.PREFACE || paragraph == null) {
            return false;
        }
        String normalizedContent = normalizeWhitespace(paragraph.content());
        String normalizedTitle = normalizeWhitespace(section.title());
        return normalizedContent.equals(normalizedTitle)
                || stripTrailingHeadingPunctuation(normalizedContent).equals(normalizedTitle);
    }

    /**
     * 判断当前位置是否以隐式目录条目块开头。
     *
     * @param paragraphs 段落列表
     * @param startIndex 起始索引
     * @return 符合隐式目录特征时返回 true
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
     *
     * @param paragraph 待统计段落
     * @return 符合目录项特征的行数
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
     *
     * @param paragraph 待判断段落
     * @return 包含目录项行时返回 true
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
     *
     * @param text 待判断文本
     * @return 符合目录项格式时返回 true
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
    private List<ChunkSlice> splitLongParagraph(SectionBlock section, ParagraphBlock paragraph, ParagraphBlock leadingHeading) {
        List<ChunkSlice> chunks = new ArrayList<>();
        String content = paragraph.content();
        int chunkSize = ragProperties.chunkSize();
        int overlap = Math.min(ragProperties.chunkOverlap(), Math.max(0, chunkSize - 1));

        int start = 0;
        boolean firstSlice = true;
        while (start < content.length()) {
            int headingLength = firstSlice && leadingHeading != null ? leadingHeading.content().length() + 2 : 0;
            int end = Math.min(content.length(), start + Math.max(1, chunkSize - headingLength));
            String chunkContent = content.substring(start, end).strip();
            if (!chunkContent.isBlank()) {
                if (firstSlice && leadingHeading != null) {
                    chunkContent = leadingHeading.content() + "\n\n" + chunkContent;
                }
                chunks.add(new ChunkSlice(
                        chunkContent,
                        firstSlice && leadingHeading != null ? leadingHeading.start() : paragraph.start() + start,
                        paragraph.start() + end,
                        section.title(),
                        section.type(),
                        section.level()
                ));
            }
            firstSlice = false;
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
    private ChunkSlice toChunkSlice(SectionBlock section, ParagraphBlock leadingHeading, List<ParagraphBlock> paragraphs) {
        List<ParagraphBlock> contentParagraphs = new ArrayList<>();
        if (leadingHeading != null) {
            contentParagraphs.add(leadingHeading);
        }
        contentParagraphs.addAll(paragraphs);
        String content = contentParagraphs.stream()
                .map(ParagraphBlock::content)
                .collect(Collectors.joining("\n\n"));
        int start = contentParagraphs.get(0).start();
        int end = contentParagraphs.get(contentParagraphs.size() - 1).end();
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
        Map<String, Object> metadata = sourceMetadataWithoutDocumentAssets(source);
        metadata.put(MetadataKeys.CHUNK_INDEX, chunkIndex);
        metadata.put(MetadataKeys.CHUNK_TYPE, CHUNK_TYPE_TEXT);
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
                UUID.nameUUIDFromBytes((source.sourceId() + "::" + chunkIndex + "::" + slice.start() + "::" + slice.end()).getBytes(StandardCharsets.UTF_8)).toString(),
                source.sourceId(),
                chunkIndex,
                slice.content(),
                metadata
        );
    }

    /**
     * 从文档资产摘要中生成图片/表格上下文 chunk。
     */
    private List<DocumentChunk> buildFigureContextChunks(DocumentSource source,
                                                         String fullText,
                                                         List<ParagraphBlock> paragraphs,
                                                         List<SectionBlock> sections) {
        List<FigureAsset> assets = extractFigureAssets(source);
        if (assets.isEmpty()) {
            return List.of();
        }

        List<DocumentChunk> chunks = new ArrayList<>();
        LinkedHashSet<String> seenAssetIds = new LinkedHashSet<>();
        for (FigureAsset asset : assets) {
            if (!seenAssetIds.add(asset.assetId())) {
                log.debug("document.split.figure-context.skip sourceId={} assetId={} reason=duplicate_asset",
                        source.sourceId(), asset.assetId());
                continue;
            }
            Optional<TextRange> captionRange = locateCaptionOffset(source.sourceId(), fullText, asset);
            if (captionRange.isEmpty()) {
                continue;
            }
            FigureContext context = selectFigureContext(asset, captionRange.get(), paragraphs, sections);
            RenderedFigureContext rendered = renderFigureContext(context);
            if (rendered.content().length() < FIGURE_CONTEXT_MIN_LENGTH) {
                log.debug("document.split.figure-context.skip sourceId={} assetId={} reason=content_too_short length={}",
                        source.sourceId(), asset.assetId(), rendered.content().length());
                continue;
            }
            DocumentChunk chunk = toFigureContextChunk(source, chunks.size(), context, rendered);
            chunks.add(chunk);
            log.debug("document.split.figure-context.chunk sourceId={} assetId={} assetType={} captionStart={} captionEnd={} chunkLength={} excerpt={}",
                    source.sourceId(), asset.assetId(), asset.assetType(), captionRange.get().start(), captionRange.get().end(),
                    rendered.content().length(), LogSanitizer.safeExcerpt(rendered.content(), 160));
        }
        return chunks;
    }

    /**
     * 抽取可用于构建 figure context 的资产摘要。
     */
    private List<FigureAsset> extractFigureAssets(DocumentSource source) {
        Object documentAssets = source.metadata() == null ? null : source.metadata().get(MetadataKeys.DOCUMENT_ASSETS);
        if (!(documentAssets instanceof List<?> assets)) {
            return List.of();
        }
        List<FigureAsset> figureAssets = new ArrayList<>();
        for (Object rawAsset : assets) {
            if (!(rawAsset instanceof Map<?, ?> asset)) {
                continue;
            }
            toFigureAsset(source, asset).ifPresent(figureAssets::add);
        }
        return figureAssets;
    }

    /**
     * 将资产 metadata map 归一化为 figure context 可消费的结构。
     */
    private Optional<FigureAsset> toFigureAsset(DocumentSource source, Map<?, ?> asset) {
        String assetType = firstNonBlankString(asset.get(MetadataKeys.ASSET_TYPE), asset.get(RAW_ASSET_TYPE));
        String normalizedType = normalizeAssetType(assetType);
        if (!FIGURE_ASSET_TYPES.contains(normalizedType)) {
            log.debug("document.split.figure-context.skip sourceId={} assetId={} reason=unsupported_asset_type assetType={}",
                    source.sourceId(), firstNonBlankString(asset.get(MetadataKeys.ASSET_ID)), assetType);
            return Optional.empty();
        }

        String caption = firstNonBlankString(
                asset.get(MetadataKeys.ASSET_CAPTION),
                asset.get(RAW_CAPTION),
                asset.get(RAW_CAPTION_TEXT),
                asset.get(RAW_EXTRACTED_TEXT)
        );
        if (caption == null) {
            log.debug("document.split.figure-context.skip sourceId={} assetId={} reason=missing_caption",
                    source.sourceId(), firstNonBlankString(asset.get(MetadataKeys.ASSET_ID)));
            return Optional.empty();
        }

        Integer page = integerValue(firstPresent(asset.get(MetadataKeys.PAGE_NUMBER), asset.get(RAW_PAGE)));
        String bbox = firstNonBlankString(asset.get(RAW_BBOX), asset.get(RAW_BOUNDING_BOX));
        String assetId = firstNonBlankString(asset.get(MetadataKeys.ASSET_ID));
        if (assetId == null) {
            assetId = stableAssetId(source.sourceId(), caption, page, bbox);
        }

        return Optional.of(new FigureAsset(
                assetId,
                normalizedType,
                caption,
                page,
                bbox,
                integerValue(asset.get(MetadataKeys.CAPTION_START)),
                integerValue(asset.get(MetadataKeys.CAPTION_END)),
                integerValue(asset.get(MetadataKeys.TEXT_START)),
                integerValue(asset.get(MetadataKeys.TEXT_END)),
                integerValue(asset.get(RAW_START)),
                integerValue(asset.get(RAW_END))
        ));
    }

    /**
     * 定位 caption 在全文中的原文范围。
     */
    private Optional<TextRange> locateCaptionOffset(String sourceId, String fullText, FigureAsset asset) {
        Optional<TextRange> captionRange = validTextRange(asset.captionStart(), asset.captionEnd(), fullText.length());
        if (captionRange.isPresent()) {
            return captionRange;
        }
        Optional<TextRange> textRange = validTextRange(asset.textStart(), asset.textEnd(), fullText.length());
        if (textRange.isPresent()) {
            return textRange;
        }
        Optional<TextRange> assetRange = validTextRange(asset.start(), asset.end(), fullText.length());
        if (assetRange.isPresent()) {
            return assetRange;
        }

        List<TextRange> matches = findCaptionMatches(fullText, asset.caption());
        if (matches.isEmpty()) {
            log.debug("document.split.figure-context.skip sourceId={} assetId={} reason=caption_not_found caption={}",
                    sourceId, asset.assetId(), LogSanitizer.safeExcerpt(asset.caption(), 120));
            return Optional.empty();
        }
        if (matches.size() == 1) {
            return Optional.of(matches.get(0));
        }

        Integer hint = firstAvailableOffset(asset.captionStart(), asset.textStart(), asset.start());
        if (hint != null) {
            return matches.stream()
                    .min(Comparator.comparingInt(match -> distanceToRange(hint, match)));
        }

        log.debug("document.split.figure-context.skip sourceId={} assetId={} reason=caption_matched_multiple count={} caption={}",
                sourceId, asset.assetId(), matches.size(), LogSanitizer.safeExcerpt(asset.caption(), 120));
        return Optional.empty();
    }

    /**
     * 基于 caption 位置选择前后正文上下文。
     */
    private FigureContext selectFigureContext(FigureAsset asset,
                                              TextRange captionRange,
                                              List<ParagraphBlock> paragraphs,
                                              List<SectionBlock> sections) {
        ParagraphBlock captionParagraph = paragraphContainingRange(paragraphs, captionRange).orElse(null);
        SectionBlock section = sectionContainingOffset(sections, captionRange.start())
                .orElseGet(() -> captionParagraph == null
                        ? new SectionBlock("", SectionType.PREFACE, 0, List.of())
                        : sectionContainingParagraph(sections, captionParagraph)
                        .orElse(new SectionBlock("", SectionType.PREFACE, 0, List.of())));
        ParagraphBlock before = captionParagraph == null
                ? null
                : nearestContextParagraph(paragraphs, sections, captionParagraph, captionRange, -1);
        if (before == null && captionParagraph != null) {
            before = adjacentContextParagraph(paragraphs, sections, captionParagraph, captionRange, -1);
        }
        ParagraphBlock after = captionParagraph == null
                ? null
                : nearestContextParagraph(paragraphs, sections, captionParagraph, captionRange, 1);
        if (after == null && captionParagraph != null) {
            after = adjacentContextParagraph(paragraphs, sections, captionParagraph, captionRange, 1);
        }
        return new FigureContext(asset, captionRange, before, after, section);
    }

    /**
     * 渲染 figure context 内容，并按 chunkSize 限制上下文长度。
     */
    private RenderedFigureContext renderFigureContext(FigureContext context) {
        String sectionTitle = sectionTitleForContent(context.section());
        String caption = context.asset().caption().strip();
        String before = contextText(context.before());
        String after = contextText(context.after());
        String content = renderFigureContextContent(sectionTitle, caption, before, after);
        int maxLength = ragProperties.chunkSize();
        if (maxLength <= 0 || content.length() <= maxLength) {
            return new RenderedFigureContext(content, before != null, after != null);
        }

        String fittedBefore = before;
        String fittedAfter = after;
        int guard = 0;
        while (content.length() > maxLength && guard++ < 12 && (fittedBefore != null || fittedAfter != null)) {
            int excess = content.length() - maxLength;
            boolean reduceBefore = fittedBefore != null && (fittedAfter == null || fittedBefore.length() >= fittedAfter.length());
            if (reduceBefore) {
                fittedBefore = shortenContext(fittedBefore, excess, true);
                if (fittedBefore.isBlank()) {
                    fittedBefore = null;
                }
            } else {
                fittedAfter = shortenContext(fittedAfter, excess, false);
                if (fittedAfter.isBlank()) {
                    fittedAfter = null;
                }
            }
            content = renderFigureContextContent(sectionTitle, caption, fittedBefore, fittedAfter);
        }
        if (content.length() > maxLength) {
            content = renderFigureContextContent(sectionTitle, caption, null, null);
            return new RenderedFigureContext(content, false, false);
        }
        return new RenderedFigureContext(content, fittedBefore != null, fittedAfter != null);
    }

    /**
     * 构建 figure context chunk。
     */
    private DocumentChunk toFigureContextChunk(DocumentSource source,
                                               int chunkIndex,
                                               FigureContext context,
                                               RenderedFigureContext rendered) {
        FigureAsset asset = context.asset();
        int chunkStart = context.captionRange().start();
        int chunkEnd = context.captionRange().end();
        if (rendered.includesBefore() && context.before() != null) {
            chunkStart = Math.min(chunkStart, context.before().start());
            chunkEnd = Math.max(chunkEnd, context.before().end());
        }
        if (rendered.includesAfter() && context.after() != null) {
            chunkStart = Math.min(chunkStart, context.after().start());
            chunkEnd = Math.max(chunkEnd, context.after().end());
        }

        Map<String, Object> metadata = sourceMetadataWithoutDocumentAssets(source);
        metadata.put(MetadataKeys.CHUNK_INDEX, chunkIndex);
        metadata.put(MetadataKeys.CHUNK_TYPE, CHUNK_TYPE_FIGURE_CONTEXT);
        metadata.put(MetadataKeys.ASSET_IDS, List.of(asset.assetId()));
        metadata.put(MetadataKeys.ASSET_ID, asset.assetId());
        metadata.put(MetadataKeys.ASSET_TYPE, asset.assetType());
        metadata.put(MetadataKeys.ASSET_CAPTION, asset.caption());
        if (asset.page() != null) {
            metadata.put(MetadataKeys.PAGE_NUMBER, asset.page());
        }
        if (asset.bbox() != null) {
            metadata.put(RAW_BBOX, asset.bbox());
        }
        metadata.put(MetadataKeys.CAPTION_START, context.captionRange().start());
        metadata.put(MetadataKeys.CAPTION_END, context.captionRange().end());
        metadata.put(MetadataKeys.SECTION_TITLE, context.section().title());
        metadata.put(MetadataKeys.SECTION_TYPE, context.section().type().name());
        metadata.put(MetadataKeys.SECTION_LEVEL, context.section().level());
        metadata.put(MetadataKeys.CHUNK_START, chunkStart);
        metadata.put(MetadataKeys.CHUNK_END, chunkEnd);
        metadata.put(MetadataKeys.CHUNK_LENGTH, rendered.content().length());
        if (rendered.includesBefore() && context.before() != null) {
            metadata.put(MetadataKeys.CONTEXT_BEFORE_START, context.before().start());
            metadata.put(MetadataKeys.CONTEXT_BEFORE_END, context.before().end());
        }
        if (rendered.includesAfter() && context.after() != null) {
            metadata.put(MetadataKeys.CONTEXT_AFTER_START, context.after().start());
            metadata.put(MetadataKeys.CONTEXT_AFTER_END, context.after().end());
        }

        String stableIdMaterial = source.sourceId()
                + "::figure-context::"
                + asset.assetId()
                + "::"
                + context.captionRange().start()
                + "::"
                + context.captionRange().end();
        return new DocumentChunk(
                UUID.nameUUIDFromBytes(stableIdMaterial.getBytes(StandardCharsets.UTF_8)).toString(),
                source.sourceId(),
                chunkIndex,
                rendered.content(),
                metadata
        );
    }

    /**
     * 合并普通 chunk 和 figure context chunk，并按原文位置重排 chunkIndex。
     */
    private List<DocumentChunk> mergeAndReindexChunks(List<DocumentChunk> textChunks, List<DocumentChunk> figureContextChunks) {
        if (textChunks.isEmpty() && figureContextChunks.isEmpty()) {
            return List.of();
        }
        List<DocumentChunk> merged = new ArrayList<>(textChunks.size() + figureContextChunks.size());
        merged.addAll(textChunks);
        merged.addAll(figureContextChunks);
        merged.sort(Comparator
                .comparingInt((DocumentChunk chunk) -> metadataInt(chunk.metadata(), MetadataKeys.CHUNK_START, Integer.MAX_VALUE))
                .thenComparingInt(this::chunkTypeOrder)
                .thenComparingInt(DocumentChunk::chunkIndex));

        List<DocumentChunk> reindexed = new ArrayList<>(merged.size());
        for (int index = 0; index < merged.size(); index++) {
            reindexed.add(withChunkIndex(merged.get(index), index));
        }
        return List.copyOf(reindexed);
    }

    /**
     * 复制来源 metadata，并过滤掉完整资产大对象。
     */
    private Map<String, Object> sourceMetadataWithoutDocumentAssets(DocumentSource source) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (source.metadata() != null) {
            source.metadata().forEach((key, value) -> {
                if (!MetadataKeys.DOCUMENT_ASSETS.equals(key)) {
                    metadata.put(key, value);
                }
            });
        }
        return metadata;
    }

    /**
     * 复制文档块并更新其索引编号。
     *
     * @param chunk 原始文档块
     * @param chunkIndex 新的索引编号
     * @return 更新索引后的文档块
     */
    private DocumentChunk withChunkIndex(DocumentChunk chunk, int chunkIndex) {
        Map<String, Object> metadata = new LinkedHashMap<>(chunk.metadata());
        metadata.put(MetadataKeys.CHUNK_INDEX, chunkIndex);
        return new DocumentChunk(chunk.chunkId(), chunk.sourceId(), chunkIndex, chunk.content(), metadata);
    }

    /**
     * 返回文档块的类型排序权重，图片上下文类型权重较高。
     *
     * @param chunk 文档块
     * @return 排序权重值
     */
    private int chunkTypeOrder(DocumentChunk chunk) {
        Object chunkType = chunk.metadata() == null ? null : chunk.metadata().get(MetadataKeys.CHUNK_TYPE);
        return CHUNK_TYPE_FIGURE_CONTEXT.equals(chunkType) ? 1 : 0;
    }

    /**
     * 从元数据中安全读取整数值，转换失败时返回默认值。
     *
     * @param metadata 元数据映射
     * @param key 键名
     * @param fallback 默认值
     * @return 整数值或默认值
     */
    private int metadataInt(Map<String, Object> metadata, String key, int fallback) {
        Integer value = metadata == null ? null : integerValue(metadata.get(key));
        return value == null ? fallback : value;
    }

    /**
     * 校验并构建有效的文本范围，超出文本长度时自动修正。
     *
     * @param start 起始偏移
     * @param end 结束偏移
     * @param textLength 文本总长度
     * @return 有效的文本范围，无效时返回空
     */
    private Optional<TextRange> validTextRange(Integer start, Integer end, int textLength) {
        if (start == null || end == null || textLength <= 0) {
            return Optional.empty();
        }
        int safeStart = Math.max(0, start);
        int safeEnd = Math.min(textLength, end);
        return safeStart < safeEnd ? Optional.of(new TextRange(safeStart, safeEnd)) : Optional.empty();
    }

    /**
     * 在全文中查找 caption 文本的所有出现位置。
     *
     * @param fullText 文档全文
     * @param caption 图片说明文本
     * @return 匹配的文本范围列表
     */
    private List<TextRange> findCaptionMatches(String fullText, String caption) {
        String needle = caption == null ? "" : caption.strip();
        if (fullText == null || needle.isBlank()) {
            return List.of();
        }
        List<TextRange> matches = new ArrayList<>();
        int fromIndex = 0;
        while (fromIndex < fullText.length()) {
            int start = fullText.indexOf(needle, fromIndex);
            if (start < 0) {
                break;
            }
            int end = start + needle.length();
            matches.add(new TextRange(start, end));
            fromIndex = Math.max(end, start + 1);
        }
        return matches;
    }

    /**
     * 返回第一个非空的偏移量值。
     *
     * @param offsets 候选偏移量列表
     * @return 第一个非空偏移量，全部为空时返回 null
     */
    private Integer firstAvailableOffset(Integer... offsets) {
        for (Integer offset : offsets) {
            if (offset != null) {
                return offset;
            }
        }
        return null;
    }

    /**
     * 计算偏移量到文本范围的最近距离，偏移量在范围内时返回 0。
     *
     * @param offset 待比较偏移量
     * @param range 文本范围
     * @return 最近距离
     */
    private int distanceToRange(int offset, TextRange range) {
        if (offset >= range.start() && offset <= range.end()) {
            return 0;
        }
        return Math.min(Math.abs(offset - range.start()), Math.abs(offset - range.end()));
    }

    /**
     * 查找完全包含或部分重叠指定文本范围的段落。
     *
     * @param paragraphs 段落列表
     * @param range 目标文本范围
     * @return 匹配的段落，不存在时返回空
     */
    private Optional<ParagraphBlock> paragraphContainingRange(List<ParagraphBlock> paragraphs, TextRange range) {
        return paragraphs.stream()
                .filter(paragraph -> paragraph.start() <= range.start() && paragraph.end() >= range.end())
                .findFirst()
                .or(() -> paragraphs.stream()
                        .filter(paragraph -> rangesOverlap(paragraph.start(), paragraph.end(), range.start(), range.end()))
                        .findFirst());
    }

    /**
     * 沿指定方向查找最近的符合条件的上下文段落。
     *
     * @param paragraphs 段落列表
     * @param sections 章节列表
     * @param captionParagraph 图片说明所在段落
     * @param captionRange 图片说明文本范围
     * @param direction 搜索方向，-1 向前，1 向后
     * @return 符合条件的上下文段落，不存在时返回 null
     */
    private ParagraphBlock nearestContextParagraph(List<ParagraphBlock> paragraphs,
                                                   List<SectionBlock> sections,
                                                   ParagraphBlock captionParagraph,
                                                   TextRange captionRange,
                                                   int direction) {
        int captionIndex = paragraphs.indexOf(captionParagraph);
        if (captionIndex < 0) {
            return null;
        }
        for (int index = captionIndex + direction; index >= 0 && index < paragraphs.size(); index += direction) {
            ParagraphBlock candidate = paragraphs.get(index);
            if (isEligibleContextParagraph(candidate, sections, captionRange)) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * 沿指定方向查找相邻的上下文段落，遇到章节边界时停止搜索。
     *
     * @param paragraphs 段落列表
     * @param sections 章节列表
     * @param captionParagraph 图片说明所在段落
     * @param captionRange 图片说明文本范围
     * @param direction 搜索方向，-1 向前，1 向后
     * @return 相邻的上下文段落，不存在时返回 null
     */
    private ParagraphBlock adjacentContextParagraph(List<ParagraphBlock> paragraphs,
                                                    List<SectionBlock> sections,
                                                    ParagraphBlock captionParagraph,
                                                    TextRange captionRange,
                                                    int direction) {
        int captionIndex = paragraphs.indexOf(captionParagraph);
        if (captionIndex < 0) {
            return null;
        }
        for (int index = captionIndex + direction; index >= 0 && index < paragraphs.size(); index += direction) {
            ParagraphBlock candidate = paragraphs.get(index);
            if (isAdjacentContextParagraph(candidate, sections, captionRange)) {
                return candidate;
            }
            if (detectHeading(candidate, false).isPresent()) {
                return null;
            }
        }
        return null;
    }

    /**
     * 判断段落是否适合作为相邻上下文，排除短段落、标题行、目录和参考文献等。
     *
     * @param paragraph 待判断段落
     * @param sections 章节列表
     * @param captionRange 图片说明文本范围
     * @return 适合作为上下文时返回 true
     */
    private boolean isAdjacentContextParagraph(ParagraphBlock paragraph, List<SectionBlock> sections, TextRange captionRange) {
        if (paragraph == null || rangesOverlap(paragraph.start(), paragraph.end(), captionRange.start(), captionRange.end())) {
            return false;
        }
        String normalized = normalizeWhitespace(paragraph.content());
        if (normalized.length() < 6) {
            return false;
        }
        Optional<SectionBlock> section = sectionContainingParagraph(sections, paragraph);
        if (section.isPresent() && (section.get().type() == SectionType.CONTENTS || section.get().type() == SectionType.REFERENCES)) {
            return false;
        }
        if (section.isPresent() && standaloneSectionHeading(section.get(), paragraph)) {
            return false;
        }
        if (detectHeading(paragraph, false).isPresent()) {
            return false;
        }
        if (containsContentsEntries(paragraph) || looksLikeCaptionParagraph(normalized) || looksLikeReferenceEntry(normalized)) {
            return false;
        }
        return !looksLikeVisualNoiseParagraph(paragraph);
    }

    /**
     * 判断段落是否满足上下文段落的最低质量要求。
     *
     * @param paragraph 待判断段落
     * @param sections 章节列表
     * @param captionRange 图片说明文本范围
     * @return 满足条件时返回 true
     */
    private boolean isEligibleContextParagraph(ParagraphBlock paragraph, List<SectionBlock> sections, TextRange captionRange) {
        if (paragraph == null || rangesOverlap(paragraph.start(), paragraph.end(), captionRange.start(), captionRange.end())) {
            return false;
        }
        String normalized = normalizeWhitespace(paragraph.content());
        if (normalized.length() < CONTEXT_PARAGRAPH_MIN_LENGTH) {
            return false;
        }
        Optional<SectionBlock> section = sectionContainingParagraph(sections, paragraph);
        if (section.isPresent() && (section.get().type() == SectionType.CONTENTS || section.get().type() == SectionType.REFERENCES)) {
            return false;
        }
        if (section.isPresent() && standaloneSectionHeading(section.get(), paragraph)) {
            return false;
        }
        if (detectHeading(paragraph, false).isPresent()) {
            return false;
        }
        if (containsContentsEntries(paragraph) || looksLikeCaptionParagraph(normalized) || looksLikeReferenceEntry(normalized)) {
            return false;
        }
        return !looksLikeVisualNoiseParagraph(paragraph);
    }

    /**
     * 查找包含指定偏移量的章节。
     *
     * @param sections 章节列表
     * @param offset 目标偏移量
     * @return 匹配的章节，不存在时返回空
     */
    private Optional<SectionBlock> sectionContainingOffset(List<SectionBlock> sections, int offset) {
        return sections.stream()
                .filter(section -> !section.paragraphs().isEmpty())
                .filter(section -> section.paragraphs().get(0).start() <= offset
                        && section.paragraphs().get(section.paragraphs().size() - 1).end() >= offset)
                .findFirst();
    }

    /**
     * 查找包含指定段落的章节。
     *
     * @param sections 章节列表
     * @param paragraph 目标段落
     * @return 匹配的章节，不存在时返回空
     */
    private Optional<SectionBlock> sectionContainingParagraph(List<SectionBlock> sections, ParagraphBlock paragraph) {
        return sections.stream()
                .filter(section -> section.paragraphs().contains(paragraph)
                        || section.paragraphs().stream().anyMatch(item -> item.start() == paragraph.start() && item.end() == paragraph.end()))
                .findFirst();
    }

    /**
     * 判断两个范围是否重叠。
     *
     * @param leftStart 左范围起始
     * @param leftEnd 左范围结束
     * @param rightStart 右范围起始
     * @param rightEnd 右范围结束
     * @return 重叠时返回 true
     */
    private boolean rangesOverlap(int leftStart, int leftEnd, int rightStart, int rightEnd) {
        return leftStart < rightEnd && leftEnd > rightStart;
    }

    /**
     * 判断段落是否像图片说明段落。
     *
     * @param normalized 归一化后的段落文本
     * @return 符合图片说明特征时返回 true
     */
    private boolean looksLikeCaptionParagraph(String normalized) {
        String lower = normalized.toLowerCase(Locale.ROOT);
        return lower.startsWith("figure ")
                || lower.startsWith("fig. ")
                || lower.startsWith("fig ")
                || lower.startsWith("table ")
                || normalized.matches("^[图表]\\s*[0-9一二三四五六七八九十]+[.．、:：\\s].*");
    }

    /**
     * 判断段落是否像参考文献条目。
     *
     * @param normalized 归一化后的段落文本
     * @return 符合参考文献特征时返回 true
     */
    private boolean looksLikeReferenceEntry(String normalized) {
        return normalized.matches("^\\[\\d+].*")
                || normalized.matches("^\\d+\\.\\s+.+")
                || normalized.matches("^[A-Z][A-Za-z-]+,\\s+.+\\(\\d{4}\\).*");
    }

    /**
     * 判断段落是否全部由视觉识别噪声组成。
     *
     * @param paragraph 待判断段落
     * @return 全为噪声行时返回 true
     */
    private boolean looksLikeVisualNoiseParagraph(ParagraphBlock paragraph) {
        List<LineBlock> lines = extractLines(paragraph.content()).stream()
                .filter(line -> !line.content().isBlank())
                .toList();
        return !lines.isEmpty() && lines.stream().allMatch(line -> isVisualArtifactLine(line.content()));
    }

    /**
     * 渲染图片上下文内容，包含章节标题、图片说明和前后文段落。
     *
     * @param sectionTitle 章节标题
     * @param caption 图片说明
     * @param before 前文上下文
     * @param after 后文上下文
     * @return 渲染后的上下文文本
     */
    private String renderFigureContextContent(String sectionTitle, String caption, String before, String after) {
        StringBuilder builder = new StringBuilder("[Figure Context]");
        if (sectionTitle != null && !sectionTitle.isBlank()) {
            builder.append("\nSection: ").append(sectionTitle.strip());
        }
        builder.append("\n\nCaption:\n").append(caption.strip());
        if (before != null && !before.isBlank()) {
            builder.append("\n\nBefore Context:\n").append(before.strip());
        }
        if (after != null && !after.isBlank()) {
            builder.append("\n\nAfter Context:\n").append(after.strip());
        }
        return builder.toString();
    }

    /**
     * 获取用于内容渲染的章节标题，前置内容章节返回空字符串。
     *
     * @param section 章节块
     * @return 章节标题或空字符串
     */
    private String sectionTitleForContent(SectionBlock section) {
        if (section == null || section.title() == null || section.title().isBlank()) {
            return "";
        }
        if (section.type() == SectionType.PREFACE && "前置内容".equals(section.title())) {
            return "";
        }
        return section.title();
    }

    /**
     * 安全获取段落的上下文文本，空段落返回 null。
     *
     * @param paragraph 段落块
     * @return 段落文本或 null
     */
    private String contextText(ParagraphBlock paragraph) {
        if (paragraph == null || paragraph.content() == null || paragraph.content().isBlank()) {
            return null;
        }
        return paragraph.content().strip();
    }

    /**
     * 缩短上下文文本至目标长度，保留头部或尾部并添加省略号。
     *
     * @param text 原始文本
     * @param excess 超出的字符数
     * @param keepEnd true 保留尾部，false 保留头部
     * @return 缩短后的文本
     */
    private String shortenContext(String text, int excess, boolean keepEnd) {
        if (text == null || text.isBlank()) {
            return "";
        }
        int targetLength = text.length() - Math.max(1, excess + 1);
        if (targetLength <= 0) {
            return "";
        }
        if (targetLength <= 2) {
            return keepEnd
                    ? text.substring(text.length() - targetLength).strip()
                    : text.substring(0, targetLength).strip();
        }
        if (keepEnd) {
            return "…" + text.substring(text.length() - targetLength + 1).stripLeading();
        }
        return text.substring(0, targetLength - 1).stripTrailing() + "…";
    }

    /**
     * 归一化资产类型字符串为小写格式。
     *
     * @param assetType 原始资产类型
     * @return 归一化后的资产类型
     */
    private String normalizeAssetType(String assetType) {
        return assetType == null ? "" : assetType.strip().toLowerCase(Locale.ROOT);
    }

    /**
     * 根据资产信息生成稳定的唯一资产 ID。
     *
     * @param sourceId 文档来源标识
     * @param caption 图片说明
     * @param page 页码
     * @param bbox 边界框
     * @return 基于内容哈希的 UUID 字符串
     */
    private String stableAssetId(String sourceId, String caption, Integer page, String bbox) {
        String material = sourceId
                + "::figure-asset::"
                + normalizeWhitespace(caption)
                + "::"
                + (page == null ? "" : page)
                + "::"
                + (bbox == null ? "" : bbox);
        return UUID.nameUUIDFromBytes(material.getBytes(StandardCharsets.UTF_8)).toString();
    }

    /**
     * 从多个候选值中返回第一个非空白字符串。
     *
     * @param values 候选值列表
     * @return 第一个非空白字符串，全部为空时返回 null
     */
    private String firstNonBlankString(Object... values) {
        for (Object value : values) {
            if (value == null) {
                continue;
            }
            String text = String.valueOf(value).strip();
            if (!text.isBlank()) {
                return text;
            }
        }
        return null;
    }

    /**
     * 从多个候选值中返回第一个非空且非空白的值。
     *
     * @param values 候选值列表
     * @return 第一个有效值，全部为空时返回 null
     */
    private Object firstPresent(Object... values) {
        for (Object value : values) {
            if (value == null) {
                continue;
            }
            if (value instanceof CharSequence sequence && sequence.toString().isBlank()) {
                continue;
            }
            return value;
        }
        return null;
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
     * 移除连续的视觉识别噪声块，避免 PDF 图形标注被当作可检索正文。
     */
    private ChunkSlice removeVisualArtifactLineRuns(ChunkSlice slice) {
        if (slice == null || slice.content() == null || slice.content().isBlank()) {
            return slice;
        }
        List<LineBlock> lines = extractLines(slice.content());
        if (lines.size() < VISUAL_ARTIFACT_RUN_MIN_LINES) {
            return slice;
        }
        List<LineBlock> keptLines = new ArrayList<>(lines.size());
        List<LineBlock> pendingArtifacts = new ArrayList<>();
        boolean removedAny = false;
        for (LineBlock line : lines) {
            if (line.content().isBlank()) {
                if (pendingArtifacts.isEmpty()) {
                    keptLines.add(line);
                } else {
                    pendingArtifacts.add(line);
                }
                continue;
            }
            if (isVisualArtifactLine(line.content())) {
                pendingArtifacts.add(line);
                continue;
            }
            removedAny = flushVisualArtifactRun(keptLines, pendingArtifacts) || removedAny;
            keptLines.add(line);
        }
        removedAny = flushVisualArtifactRun(keptLines, pendingArtifacts) || removedAny;
        if (!removedAny) {
            return slice;
        }
        String cleanedContent = keptLines.stream()
                .map(LineBlock::content)
                .collect(Collectors.joining("\n"))
                .strip();
        return new ChunkSlice(cleanedContent, slice.start(), slice.end(), slice.sectionTitle(), slice.sectionType(), slice.sectionLevel());
    }

    /**
     * 只有噪声行数量达到阈值时才丢弃；空行只作为噪声块内部间隔，不计数。
     */
    private boolean flushVisualArtifactRun(List<LineBlock> keptLines, List<LineBlock> pendingArtifacts) {
        if (pendingArtifacts.isEmpty()) {
            return false;
        }
        long artifactLineCount = pendingArtifacts.stream()
                .filter(line -> !line.content().isBlank())
                .count();
        if (artifactLineCount >= VISUAL_ARTIFACT_RUN_MIN_LINES) {
            pendingArtifacts.clear();
            return true;
        }
        keptLines.addAll(pendingArtifacts);
        pendingArtifacts.clear();
        return false;
    }

    /**
     * 判断单行是否像 PDF 图片 OCR 后产生的低信息碎片。
     */
    private boolean isVisualArtifactLine(String text) {
        String normalized = normalizeWhitespace(text);
        if (normalized.isBlank()) {
            return false;
        }
        String compact = normalized.replace(" ", "");
        if (compact.length() > 4) {
            return false;
        }
        long lettersOrDigits = compact.chars().filter(Character::isLetterOrDigit).count();
        if (lettersOrDigits == 0) {
            return true;
        }
        if (lettersOrDigits <= 2) {
            return true;
        }
        boolean digitsOnly = compact.chars().allMatch(Character::isDigit);
        return digitsOnly || lettersOrDigits < compact.length();
    }

    /**
     * 以调试级别输出章节结构信息。
     *
     * @param sourceId 文档来源标识
     * @param sections 章节列表
     */
    private void logSections(String sourceId, List<SectionBlock> sections) {
        if (!log.isDebugEnabled()) {
            return;
        }
        for (int index = 0; index < sections.size(); index++) {
            SectionBlock section = sections.get(index);
            log.debug("document.split.section sourceId={} sectionIndex={} title={} type={} level={} paragraphCount={}",
                    sourceId,
                    index,
                    LogSanitizer.safeExcerpt(section.title(), 120),
                    section.type(),
                    section.level(),
                    section.paragraphs().size());
        }
    }

    /**
     * 判断切片是否仅包含标题而无正文内容。
     *
     * @param slice 待判断切片
     * @return 仅包含标题时返回 true
     */
    private boolean isTitleOnlyChunk(ChunkSlice slice) {
        if (slice == null) {
            return false;
        }
        String normalizedContent = LogSanitizer.normalizeWhitespace(slice.content());
        String normalizedTitle = LogSanitizer.normalizeWhitespace(slice.sectionTitle());
        return !normalizedTitle.isBlank()
                && (normalizedContent.equals(normalizedTitle)
                || stripTrailingHeadingPunctuation(normalizedContent).equals(normalizedTitle));
    }

    /**
     * 安全获取文本长度。
     *
     * @param text 文本内容
     * @return 文本长度
     */
    private int textLength(String text) {
        return text == null ? 0 : text.length();
    }

    /**
     * 计算从指定起始时间到当前的耗时（毫秒）。
     *
     * @param startNanos 起始时间（纳秒）
     * @return 耗时毫秒数
     */
    private long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
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

    private record FigureAsset(String assetId,
                               String assetType,
                               String caption,
                               Integer page,
                               String bbox,
                               Integer captionStart,
                               Integer captionEnd,
                               Integer textStart,
                               Integer textEnd,
                               Integer start,
                               Integer end) {
    }

    private record TextRange(int start, int end) {
    }

    private record FigureContext(FigureAsset asset,
                                 TextRange captionRange,
                                 ParagraphBlock before,
                                 ParagraphBlock after,
                                 SectionBlock section) {
    }

    private record RenderedFigureContext(String content, boolean includesBefore, boolean includesAfter) {
    }

    private record DetectedHeading(String title, SectionType type, int level) {
    }

    private record SectionBlock(String title, SectionType type, int level, List<ParagraphBlock> paragraphs) {
    }

    private static final class SplitSkipStats {
        private int emptyCount;
        private int titleOnlyCount;
        private int tooShortCount;
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
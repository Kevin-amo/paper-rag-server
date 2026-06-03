package com.lqr.paperragserver.agent.paper;

import com.lqr.paperragserver.common.logging.LogSanitizer;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.regex.Pattern;

@Component
public class CitationFilter {

    private static final Pattern NUMBERED_TITLE_PATTERN = Pattern.compile("^\\d+(?:\\.\\d+){0,4}(?:\\s*[.．、)]\\s*|\\s+)(?:abstract|introduction|related work|related works|methods?|experiments?|results?|discussion|conclusions?|references|bibliography)\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern CONTENTS_ENTRY_PATTERN = Pattern.compile("^.+?(?:\\.{2,}|…{2,}|\\s{2,}|\\t+)\\s*(?:\\d+|[ivxlcdm]+)\\s*$", Pattern.CASE_INSENSITIVE);

    /**
     * 判断文本是否适合作为可展示引用摘录，并返回过滤原因。
     *
     * @param content 候选引用内容
     * @return 引用展示过滤原因
     */
    public Reason reason(String content) {
        String normalized = LogSanitizer.normalizeWhitespace(content);
        if (normalized.isBlank()) {
            return Reason.EMPTY;
        }
        if (normalized.length() <= 3) {
            return Reason.TOO_SHORT;
        }
        String canonical = stripTrailingHeadingPunctuation(normalized).toLowerCase(Locale.ROOT);
        if (isStandaloneHeading(canonical) || NUMBERED_TITLE_PATTERN.matcher(normalized).matches()) {
            return Reason.TITLE_ONLY;
        }
        if (CONTENTS_ENTRY_PATTERN.matcher(normalized).matches()) {
            return Reason.CONTENTS_ENTRY;
        }
        return Reason.DISPLAYABLE;
    }

    /**
     * 判断文本是否可以直接展示为引用摘录。
     *
     * @param content 候选引用内容
     * @return 是否可展示
     */
    public boolean displayable(String content) {
        return reason(content) == Reason.DISPLAYABLE;
    }

    /**
     * 判断规范化文本是否为独立章节标题。
     *
     * @param canonical 已小写化的规范文本
     * @return 是否为独立标题
     */
    private boolean isStandaloneHeading(String canonical) {
        return canonical.equals("abstract")
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
                || canonical.equals("bibliography");
    }

    /**
     * 去除标题末尾常见冒号，便于章节标题判定。
     *
     * @param content 原始内容
     * @return 去除末尾标题标点后的内容
     */
    private String stripTrailingHeadingPunctuation(String content) {
        String normalized = LogSanitizer.normalizeWhitespace(content);
        while (normalized.endsWith(":") || normalized.endsWith("：")) {
            normalized = normalized.substring(0, normalized.length() - 1).strip();
        }
        return normalized;
    }

    public enum Reason {
        /** 可展示 */
        DISPLAYABLE,
        /** 内容为空 */
        EMPTY,
        /** 内容过短 */
        TOO_SHORT,
        /** 纯标题内容 */
        TITLE_ONLY,
        /** 目录项内容 */
        CONTENTS_ENTRY
    }

    public static final class Stats {
        private int emptyCount;
        private int tooShortCount;
        private int titleOnlyCount;
        private int contentsEntryCount;

        /**
         * 累加指定过滤原因对应的统计计数。
         *
         * @param reason 过滤原因
         */
        public void increment(Reason reason) {
            switch (reason) {
                case EMPTY -> emptyCount++;
                case TOO_SHORT -> tooShortCount++;
                case TITLE_ONLY -> titleOnlyCount++;
                case CONTENTS_ENTRY -> contentsEntryCount++;
                case DISPLAYABLE -> {
                }
            }
        }

        /**
         * 返回空内容过滤数量。
         *
         * @return 空内容数量
         */
        public int emptyCount() {
            return emptyCount;
        }

        /**
         * 返回过短内容过滤数量。
         *
         * @return 过短内容数量
         */
        public int tooShortCount() {
            return tooShortCount;
        }

        /**
         * 返回纯标题内容过滤数量。
         *
         * @return 纯标题内容数量
         */
        public int titleOnlyCount() {
            return titleOnlyCount;
        }

        /**
         * 返回目录项内容过滤数量。
         *
         * @return 目录项内容数量
         */
        public int contentsEntryCount() {
            return contentsEntryCount;
        }
    }
}
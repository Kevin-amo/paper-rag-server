package com.lqr.paperragserver.agent.paper;

import com.lqr.paperragserver.common.logging.LogSanitizer;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.regex.Pattern;

@Component
public class CitationFilter {

    private static final Pattern NUMBERED_TITLE_PATTERN = Pattern.compile("^\\d+(?:\\.\\d+){0,4}(?:\\s*[.．、)]\\s*|\\s+)(?:abstract|introduction|related work|related works|methods?|experiments?|results?|discussion|conclusions?|references|bibliography)\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern CONTENTS_ENTRY_PATTERN = Pattern.compile("^.+?(?:\\.{2,}|…{2,}|\\s{2,}|\\t+)\\s*(?:\\d+|[ivxlcdm]+)\\s*$", Pattern.CASE_INSENSITIVE);

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

    public boolean displayable(String content) {
        return reason(content) == Reason.DISPLAYABLE;
    }

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

    private String stripTrailingHeadingPunctuation(String content) {
        String normalized = LogSanitizer.normalizeWhitespace(content);
        while (normalized.endsWith(":") || normalized.endsWith("：")) {
            normalized = normalized.substring(0, normalized.length() - 1).strip();
        }
        return normalized;
    }

    public enum Reason {
        DISPLAYABLE,
        EMPTY,
        TOO_SHORT,
        TITLE_ONLY,
        CONTENTS_ENTRY
    }

    public static final class Stats {
        private int emptyCount;
        private int tooShortCount;
        private int titleOnlyCount;
        private int contentsEntryCount;

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

        public int emptyCount() {
            return emptyCount;
        }

        public int tooShortCount() {
            return tooShortCount;
        }

        public int titleOnlyCount() {
            return titleOnlyCount;
        }

        public int contentsEntryCount() {
            return contentsEntryCount;
        }
    }
}
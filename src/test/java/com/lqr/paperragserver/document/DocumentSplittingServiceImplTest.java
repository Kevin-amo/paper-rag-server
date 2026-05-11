package com.lqr.paperragserver.document;

import com.lqr.paperragserver.common.DocumentChunk;
import com.lqr.paperragserver.common.DocumentSource;
import com.lqr.paperragserver.config.RagProperties;
import com.lqr.paperragserver.document.impl.DocumentSplittingServiceImpl;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentSplittingServiceImplTest {

    @Test
    void splitShouldRecognizeEnglishContentsAndKeepStableIds() {
        DocumentSplittingServiceImpl service = new DocumentSplittingServiceImpl(new RagProperties(200, 20, 5, 0));
        DocumentSource source = new DocumentSource("source-1", "Deep Learning for RAG", "paper.pdf", Map.of("title", "Deep Learning for RAG"));
        String text = """
                Deep Learning for RAG

                Contents
                1 Introduction ........ 1
                1.1 Method ........ 3

                摘要
                本文介绍结构化切分。
                本文强调标题、摘要、目录、结论和参考文献的识别。

                1 引言
                引言段落一。

                1.1 方法
                方法段落一。

                结论
                本文总结方法效果。

                参考文献
                [1] Alice. Paper RAG.
                """;

        List<DocumentChunk> first = service.split(source, text);
        List<DocumentChunk> second = service.split(source, text);

        assertThat(first).hasSize(7);
        assertThat(first).extracting(DocumentChunk::chunkIndex).containsExactly(0, 1, 2, 3, 4, 5, 6);
        assertThat(first).extracting(chunk -> String.valueOf(chunk.metadata().get("sectionType")))
                .containsExactly("TITLE", "CONTENTS", "ABSTRACT", "SECTION", "SECTION", "CONCLUSION", "REFERENCES");
        assertThat(first).extracting(chunk -> String.valueOf(chunk.metadata().get("sectionTitle")))
                .containsExactly("Deep Learning for RAG", "Contents", "摘要", "1 引言", "1.1 方法", "结论", "参考文献");
        assertThat(first).extracting(chunk -> ((Number) chunk.metadata().get("sectionLevel")).intValue())
                .containsExactly(0, 1, 1, 1, 2, 1, 1);
        assertThat(first.get(1).content()).contains("Contents").contains("1 Introduction ........ 1").contains("1.1 Method ........ 3");
        assertThat(first).extracting(DocumentChunk::chunkId)
                .containsExactlyElementsOf(second.stream().map(DocumentChunk::chunkId).toList());
    }

    @Test
    void splitShouldSplitChineseContentsByEntriesBeforeFallbackToLength() {
        DocumentSplittingServiceImpl service = new DocumentSplittingServiceImpl(new RagProperties(26, 4, 5, 0));
        DocumentSource source = new DocumentSource("source-contents-cn", "结构化目录", "paper.pdf", Map.of("title", "结构化目录"));
        String text = """
                结构化目录

                目录
                1 引言 ........ 1
                1.1 方法设计 ........ 3
                2 实验结果 ........ 5

                结论
                目录切分需要保留目录项边界。
                """;

        List<DocumentChunk> chunks = service.split(source, text);
        List<DocumentChunk> contentsChunks = chunks.stream()
                .filter(chunk -> "CONTENTS".equals(chunk.metadata().get("sectionType")))
                .toList();

        assertThat(contentsChunks).hasSize(3);
        assertThat(contentsChunks).extracting(DocumentChunk::content)
                .containsExactly(
                        "目录\n\n1 引言 ........ 1",
                        "1.1 方法设计 ........ 3",
                        "2 实验结果 ........ 5"
                );
        assertThat(contentsChunks).allMatch(chunk -> "目录".equals(chunk.metadata().get("sectionTitle")));
        assertThat(contentsChunks).allMatch(chunk -> ((Number) chunk.metadata().get("chunkLength")).intValue() <= 26);
        assertThat(chunks).extracting(chunk -> String.valueOf(chunk.metadata().get("sectionType")))
                .containsExactly("TITLE", "CONTENTS", "CONTENTS", "CONTENTS", "CONCLUSION");
    }

    @Test
    void splitShouldFallbackToLengthForLongParagraphsInsideASection() {
        DocumentSplittingServiceImpl service = new DocumentSplittingServiceImpl(new RagProperties(60, 10, 5, 0));
        DocumentSource source = new DocumentSource("source-2", "Paper B", "paper.pdf", Map.of("title", "Paper B"));
        String text = """
                1 方法
                这是一个非常长的段落，用来验证结构块在超过长度阈值之后仍然会继续切分，并且保留同一个章节标题和章节类型。我们继续追加内容，确保它足够长。
                """;

        List<DocumentChunk> chunks = service.split(source, text);

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks).allMatch(chunk -> "1 方法".equals(chunk.metadata().get("sectionTitle")));
        assertThat(chunks).allMatch(chunk -> "SECTION".equals(chunk.metadata().get("sectionType")));
        assertThat(chunks).allMatch(chunk -> ((Number) chunk.metadata().get("chunkLength")).intValue() <= 60);
        assertThat(chunks).extracting(DocumentChunk::chunkIndex)
                .containsExactlyElementsOf(java.util.stream.IntStream.range(0, chunks.size()).boxed().toList());
        assertThat(chunks.get(0).content()).contains("1 方法");
    }

    @Test
    void splitShouldReturnEmptyListForBlankText() {
        DocumentSplittingServiceImpl service = new DocumentSplittingServiceImpl(new RagProperties(8, 2, 5, 0));
        DocumentSource source = new DocumentSource("source-1", "Paper A", "paper.pdf", Map.of());

        assertThat(service.split(source, "   ")).isEmpty();
    }
}
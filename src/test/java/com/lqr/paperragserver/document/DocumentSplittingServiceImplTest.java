package com.lqr.paperragserver.document;

import com.lqr.paperragserver.common.model.DocumentChunk;
import com.lqr.paperragserver.common.model.DocumentSource;
import com.lqr.paperragserver.rag.config.RagProperties;
import com.lqr.paperragserver.document.service.impl.DocumentSplittingServiceImpl;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentSplittingServiceImplTest {

    @Test
    void splitShouldRecognizeEnglishContentsAndKeepStableIds() {
        DocumentSplittingServiceImpl service = new DocumentSplittingServiceImpl(new RagProperties(200, 20, 5, 0));
        DocumentSource source = new DocumentSource("source-1", "Deep Learning for RAG", "paper.pdf", Map.of(
                "title", "Deep Learning for RAG",
                "extractionMode", "MULTIMODAL",
                "renderedPageCount", 2
        ));
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

        assertThat(first).hasSize(6);
        assertThat(first).extracting(DocumentChunk::chunkIndex).containsExactly(0, 1, 2, 3, 4, 5);
        assertThat(first).extracting(chunk -> String.valueOf(chunk.metadata().get("sectionType")))
                .containsExactly("CONTENTS", "ABSTRACT", "SECTION", "SECTION", "CONCLUSION", "REFERENCES");
        assertThat(first).extracting(chunk -> String.valueOf(chunk.metadata().get("sectionTitle")))
                .containsExactly("Contents", "摘要", "1 引言", "1.1 方法", "结论", "参考文献");
        assertThat(first).extracting(chunk -> ((Number) chunk.metadata().get("sectionLevel")).intValue())
                .containsExactly(1, 1, 1, 2, 1, 1);
        assertThat(first.get(0).content()).contains("Contents").contains("1 Introduction ........ 1").contains("1.1 Method ........ 3");
        assertThat(first).extracting(DocumentChunk::chunkId)
                .containsExactlyElementsOf(second.stream().map(DocumentChunk::chunkId).toList());
        assertThat(first).allSatisfy(chunk -> {
            assertThat(chunk.metadata()).containsEntry("extractionMode", "MULTIMODAL");
            assertThat(chunk.metadata()).containsEntry("renderedPageCount", 2);
        });
    }

    @Test
    void splitShouldKeepChineseContentsAsSingleChunk() {
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

        assertThat(contentsChunks).hasSize(1);
        assertThat(contentsChunks.get(0).content())
                .contains("目录")
                .contains("1 引言 ........ 1")
                .contains("1.1 方法设计 ........ 3")
                .contains("2 实验结果 ........ 5");
        assertThat(contentsChunks.get(0).metadata()).containsEntry("sectionTitle", "目录");
        assertThat(chunks).extracting(chunk -> String.valueOf(chunk.metadata().get("sectionType")))
                .containsExactly("CONTENTS", "CONCLUSION");
    }

    @Test
    void splitShouldKeepImplicitContentsEntriesAsSingleChunk() {
        DocumentSplittingServiceImpl service = new DocumentSplittingServiceImpl(new RagProperties(800, 120, 5, 0));
        DocumentSource source = new DocumentSource("source-implicit-contents", "软件分析与建模", "paper.docx", Map.of());
        String text = """
                软件分析与建模技术课程设计

                1 引言1
                1.1 项目背景2
                1.2 研究内容3
                1.3 论文结构3
                2 需求分析3
                2.1 业务分析3

                1 引言
                正文里的引言内容。

                1.1 项目背景
                正文里的项目背景内容。
                """;

        List<DocumentChunk> chunks = service.split(source, text);
        List<DocumentChunk> contentsChunks = chunks.stream()
                .filter(chunk -> "CONTENTS".equals(chunk.metadata().get("sectionType")))
                .toList();

        assertThat(contentsChunks).hasSize(1);
        assertThat(contentsChunks.get(0).content())
                .contains("1 引言1")
                .contains("1.1 项目背景2")
                .contains("2.1 业务分析3");
        assertThat(contentsChunks.get(0).metadata()).containsEntry("sectionTitle", "目录");
        assertThat(chunks).extracting(chunk -> String.valueOf(chunk.metadata().get("sectionType")))
                .containsExactly("CONTENTS", "SECTION", "SECTION");
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
    void splitShouldAttachAssetIdsToOverlappingChunks() {
        DocumentSplittingServiceImpl service = new DocumentSplittingServiceImpl(new RagProperties(800, 120, 5, 0));
        DocumentSource source = new DocumentSource("source-assets", "Asset Paper", "paper.docx", Map.of(
                "documentAssets", List.of(Map.of(
                        "assetId", "asset-1",
                        "textStart", 12,
                        "textEnd", 28
                ))
        ));
        String text = """
                1 图示说明
                这段文字前有图。图中展示系统架构。这里继续描述。
                """;

        List<DocumentChunk> chunks = service.split(source, text);

        assertThat(chunks).hasSize(1);
        assertThat(chunks.getFirst().metadata()).containsEntry("assetIds", List.of("asset-1"));
        assertThat(chunks.getFirst().metadata()).doesNotContainKey("documentAssets");
    }

    @Test
    void splitShouldMergeStandaloneAbstractHeadingIntoFollowingBody() {
        DocumentSplittingServiceImpl service = new DocumentSplittingServiceImpl(new RagProperties(800, 120, 5, 0));
        DocumentSource source = new DocumentSource("source-abstract", "Paper Abstract", "paper.pdf", Map.of("title", "Paper Abstract"));
        String text = """
                Abstract

                This paper proposes a retrieval augmented generation method.
                """;

        List<DocumentChunk> chunks = service.split(source, text);

        assertThat(chunks).hasSize(1);
        assertThat(chunks).noneMatch(chunk -> "Abstract".equals(chunk.content()));
        assertThat(chunks.getFirst().content())
                .contains("Abstract")
                .contains("This paper proposes a retrieval augmented generation method.");
        assertThat(chunks.getFirst().metadata()).containsEntry("sectionType", "ABSTRACT");
    }

    @Test
    void splitShouldMergeNumberedIntroductionHeadingIntoFollowingBody() {
        DocumentSplittingServiceImpl service = new DocumentSplittingServiceImpl(new RagProperties(800, 120, 5, 0));
        DocumentSource source = new DocumentSource("source-introduction", "Paper Introduction", "paper.pdf", Map.of("title", "Paper Introduction"));
        String text = """
                1 Introduction

                Retrieval augmented generation combines retrieval with generation.
                """;

        List<DocumentChunk> chunks = service.split(source, text);

        assertThat(chunks).hasSize(1);
        assertThat(chunks).noneMatch(chunk -> "1 Introduction".equals(chunk.content()));
        assertThat(chunks.getFirst().content())
                .contains("1 Introduction")
                .contains("Retrieval augmented generation combines retrieval with generation.");
        assertThat(chunks.getFirst().metadata()).containsEntry("sectionTitle", "1 Introduction");
    }

    @Test
    void splitShouldKeepAbstractWithInlineBody() {
        DocumentSplittingServiceImpl service = new DocumentSplittingServiceImpl(new RagProperties(800, 120, 5, 0));
        DocumentSource source = new DocumentSource("source-inline-abstract", "Inline Abstract", "paper.pdf", Map.of("title", "Inline Abstract"));
        String text = "Abstract: This paper proposes a retrieval augmented generation method.";

        List<DocumentChunk> chunks = service.split(source, text);

        assertThat(chunks).hasSize(1);
        assertThat(chunks.getFirst().content()).isEqualTo("Abstract: This paper proposes a retrieval augmented generation method.");
        assertThat(chunks.getFirst().metadata()).containsEntry("sectionTitle", "Abstract");
    }
    @Test
    void splitShouldReturnEmptyListForBlankText() {
        DocumentSplittingServiceImpl service = new DocumentSplittingServiceImpl(new RagProperties(8, 2, 5, 0));
        DocumentSource source = new DocumentSource("source-1", "Paper A", "paper.pdf", Map.of());

        assertThat(service.split(source, "   ")).isEmpty();
    }
}
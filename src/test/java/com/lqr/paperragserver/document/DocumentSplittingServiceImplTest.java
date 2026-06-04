package com.lqr.paperragserver.document;

import com.lqr.paperragserver.common.constant.MetadataKeys;
import com.lqr.paperragserver.common.model.DocumentChunk;
import com.lqr.paperragserver.common.model.DocumentSource;
import com.lqr.paperragserver.rag.config.RagProperties;
import com.lqr.paperragserver.document.service.impl.DocumentSplittingServiceImpl;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 文档切分服务的目录识别、章节边界和稳定切片 ID 测试。
 */
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
    void splitShouldKeepOnlyTextChunksWhenDocumentAssetsAbsent() {
        DocumentSplittingServiceImpl service = new DocumentSplittingServiceImpl(new RagProperties(800, 120, 5, 0));
        DocumentSource source = new DocumentSource("source-no-assets", "No Asset Paper", "paper.pdf", Map.of("title", "No Asset Paper"));
        String text = """
                1 Methods

                The baseline retrieval method keeps ordinary text chunks unchanged for vector retrieval.
                """;

        List<DocumentChunk> chunks = service.split(source, text);

        assertThat(chunks).hasSize(1);
        assertThat(chunks.getFirst().metadata()).containsEntry(MetadataKeys.CHUNK_TYPE, "TEXT");
        assertThat(chunks.getFirst().metadata()).doesNotContainKey(MetadataKeys.DOCUMENT_ASSETS);
        assertThat(chunks).noneMatch(chunk -> "FIGURE_CONTEXT".equals(chunk.metadata().get(MetadataKeys.CHUNK_TYPE)));
    }

    @Test
    void splitShouldGenerateFigureContextChunkWithCaptionAndNeighborParagraphs() {
        DocumentSplittingServiceImpl service = new DocumentSplittingServiceImpl(new RagProperties(1200, 120, 5, 0));
        String caption = "Figure 1: The proposed architecture connects retrieval, ranking, and answer grounding.";
        String before = "The paragraph before the figure explains why retrieval context must preserve nearby semantic evidence for downstream reasoning.";
        String after = "The paragraph after the figure describes how each module exchanges signals during the paper question answering workflow.";
        DocumentSource source = new DocumentSource("source-figure", "Figure Paper", "paper.pdf", Map.of(
                MetadataKeys.TITLE, "Figure Paper",
                MetadataKeys.DOCUMENT_ASSETS, List.of(Map.of(
                        MetadataKeys.ASSET_ID, "fig-1",
                        MetadataKeys.ASSET_TYPE, "figure",
                        MetadataKeys.ASSET_CAPTION, caption,
                        MetadataKeys.PAGE_NUMBER, 3,
                        "bbox", "10,10,100,100"
                ))
        ));
        String text = """
                1 Methods

                %s

                %s

                %s
                """.formatted(before, caption, after);

        List<DocumentChunk> chunks = service.split(source, text);
        List<DocumentChunk> figureChunks = chunks.stream()
                .filter(chunk -> "FIGURE_CONTEXT".equals(chunk.metadata().get(MetadataKeys.CHUNK_TYPE)))
                .toList();
        List<DocumentChunk> textChunks = chunks.stream()
                .filter(chunk -> "TEXT".equals(chunk.metadata().get(MetadataKeys.CHUNK_TYPE)))
                .toList();

        assertThat(textChunks).hasSize(1);
        assertThat(textChunks.getFirst().content()).contains(caption);
        assertThat(figureChunks).hasSize(1);
        DocumentChunk figureChunk = figureChunks.getFirst();
        assertThat(figureChunk.content())
                .contains("[Figure Context]")
                .contains("Section: 1 Methods")
                .contains("Caption:\n" + caption)
                .contains("Before Context:\n" + before)
                .contains("After Context:\n" + after)
                .doesNotContain("10,10,100,100");
        assertThat(figureChunk.metadata())
                .containsEntry(MetadataKeys.CHUNK_TYPE, "FIGURE_CONTEXT")
                .containsEntry(MetadataKeys.ASSET_IDS, List.of("fig-1"))
                .containsEntry(MetadataKeys.ASSET_ID, "fig-1")
                .containsEntry(MetadataKeys.ASSET_TYPE, "figure")
                .containsEntry(MetadataKeys.ASSET_CAPTION, caption)
                .containsEntry(MetadataKeys.SECTION_TITLE, "1 Methods")
                .containsEntry(MetadataKeys.SECTION_TYPE, "SECTION")
                .containsEntry(MetadataKeys.SECTION_LEVEL, 1);
        assertThat(figureChunk.metadata()).containsKeys(
                MetadataKeys.CHUNK_START,
                MetadataKeys.CHUNK_END,
                MetadataKeys.CHUNK_LENGTH,
                MetadataKeys.CONTEXT_BEFORE_START,
                MetadataKeys.CONTEXT_BEFORE_END,
                MetadataKeys.CONTEXT_AFTER_START,
                MetadataKeys.CONTEXT_AFTER_END
        );
    }

    @Test
    void splitShouldSkipFigureContextWhenCaptionCannotBeLocated() {
        DocumentSplittingServiceImpl service = new DocumentSplittingServiceImpl(new RagProperties(800, 120, 5, 0));
        DocumentSource source = new DocumentSource("source-missing-caption", "Missing Caption", "paper.pdf", Map.of(
                MetadataKeys.DOCUMENT_ASSETS, List.of(Map.of(
                        MetadataKeys.ASSET_ID, "fig-missing",
                        MetadataKeys.ASSET_TYPE, "figure",
                        MetadataKeys.ASSET_CAPTION, "Figure 9: This caption is absent from the parsed text."
                ))
        ));
        String text = """
                1 Results

                The parsed text only contains surrounding body paragraphs and no matching caption.
                """;

        List<DocumentChunk> chunks = service.split(source, text);

        assertThat(chunks).hasSize(1);
        assertThat(chunks).noneMatch(chunk -> "FIGURE_CONTEXT".equals(chunk.metadata().get(MetadataKeys.CHUNK_TYPE)));
    }

    @Test
    void splitShouldGenerateOnlyOneFigureContextChunkForDuplicateAsset() {
        DocumentSplittingServiceImpl service = new DocumentSplittingServiceImpl(new RagProperties(1200, 120, 5, 0));
        String caption = "Table 2: Ablation results demonstrate that contextual caption chunks improve recall.";
        Map<String, Object> asset = Map.of(
                MetadataKeys.ASSET_ID, "table-2",
                MetadataKeys.ASSET_TYPE, "table",
                MetadataKeys.ASSET_CAPTION, caption
        );
        DocumentSource source = new DocumentSource("source-duplicate-asset", "Duplicate Asset", "paper.pdf", Map.of(
                MetadataKeys.DOCUMENT_ASSETS, List.of(asset, asset)
        ));
        String text = """
                2 Experiments

                Before the table, the paper defines the compared retrieval variants and evaluation metrics.

                %s

                After the table, the paper discusses the most important recall improvements and limitations.
                """.formatted(caption);

        List<DocumentChunk> chunks = service.split(source, text);
        List<DocumentChunk> figureChunks = chunks.stream()
                .filter(chunk -> "FIGURE_CONTEXT".equals(chunk.metadata().get(MetadataKeys.CHUNK_TYPE)))
                .toList();

        assertThat(figureChunks).hasSize(1);
        assertThat(figureChunks.getFirst().metadata()).containsEntry(MetadataKeys.ASSET_IDS, List.of("table-2"));
    }

    @Test
    void splitShouldLimitLongFigureContextToChunkSizeWithoutTruncatingCaption() {
        DocumentSplittingServiceImpl service = new DocumentSplittingServiceImpl(new RagProperties(260, 40, 5, 0));
        String before = "Before context " + "describes retrieval calibration and semantic evidence preservation ".repeat(8);
        String caption = "Figure 3: The pipeline aligns dense retrieval, chunk ranking, and answer grounding.";
        String after = "After context " + "summarizes evaluation robustness and downstream answer quality ".repeat(8);
        DocumentSource source = new DocumentSource("source-long-context", "Long Context", "paper.pdf", Map.of(
                MetadataKeys.DOCUMENT_ASSETS, List.of(Map.of(
                        MetadataKeys.ASSET_ID, "fig-long",
                        MetadataKeys.ASSET_TYPE, "image",
                        MetadataKeys.ASSET_CAPTION, caption
                ))
        ));
        String text = """
                3 Evaluation

                %s

                %s

                %s
                """.formatted(before, caption, after);

        List<DocumentChunk> chunks = service.split(source, text);
        DocumentChunk figureChunk = chunks.stream()
                .filter(chunk -> "FIGURE_CONTEXT".equals(chunk.metadata().get(MetadataKeys.CHUNK_TYPE)))
                .findFirst()
                .orElseThrow();

        assertThat(figureChunk.content()).contains(caption);
        assertThat(figureChunk.content().length()).isLessThanOrEqualTo(260);
        assertThat(((Number) figureChunk.metadata().get(MetadataKeys.CHUNK_LENGTH)).intValue()).isLessThanOrEqualTo(260);
    }

    @Test
    void splitShouldStillCreateFigureContextWhenCaptionExistsInTextChunk() {
        DocumentSplittingServiceImpl service = new DocumentSplittingServiceImpl(new RagProperties(1200, 120, 5, 0));
        String caption = "Figure 4: Caption text is already present in the ordinary text chunk.";
        DocumentSource source = new DocumentSource("source-caption-in-text", "Caption In Text", "paper.pdf", Map.of(
                MetadataKeys.DOCUMENT_ASSETS, List.of(Map.of(
                        MetadataKeys.ASSET_ID, "fig-4",
                        MetadataKeys.ASSET_TYPE, "figure",
                        MetadataKeys.ASSET_CAPTION, caption
                ))
        ));
        String text = """
                4 Discussion

                Before the figure, this paragraph provides enough natural language context for retrieval.

                %s

                After the figure, this paragraph adds interpretation that should also be searchable.
                """.formatted(caption);

        List<DocumentChunk> chunks = service.split(source, text);
        List<DocumentChunk> textChunks = chunks.stream()
                .filter(chunk -> "TEXT".equals(chunk.metadata().get(MetadataKeys.CHUNK_TYPE)))
                .toList();
        List<DocumentChunk> figureChunks = chunks.stream()
                .filter(chunk -> "FIGURE_CONTEXT".equals(chunk.metadata().get(MetadataKeys.CHUNK_TYPE)))
                .toList();

        assertThat(textChunks).anySatisfy(chunk -> assertThat(chunk.content()).contains(caption));
        assertThat(figureChunks).hasSize(1);
        assertThat(figureChunks.getFirst().content()).contains(caption);
        assertThat(figureChunks.getFirst().chunkId()).isNotEqualTo(textChunks.getFirst().chunkId());
    }

    @Test
    void splitShouldCreateFigureContextForWordEmbeddedImageText() {
        DocumentSplittingServiceImpl service = new DocumentSplittingServiceImpl(new RagProperties(1200, 120, 5, 0));
        String imageText = "图中展示系统架构，包括检索模块、排序模块和答案生成模块之间的关系，并说明这些模块如何围绕文档资产形成完整的数据流。";
        String before = "图片前的正文说明系统设计目标，需要把文档资产与周围语义一起用于检索。";
        String after = "图片后的正文继续解释各模块之间的数据流向和状态变化。";
        String imageBlock = "【图片：image1.png】\n" + imageText;
        String text = String.join("\n\n", "2 系统设计", before, imageBlock, after);
        int textStart = text.indexOf(imageBlock);
        int textEnd = textStart + imageBlock.length();
        DocumentSource source = new DocumentSource("source-word-image", "Word Image", "paper.docx", Map.of(
                MetadataKeys.DOCUMENT_ASSETS, List.of(Map.of(
                        MetadataKeys.ASSET_ID, "word-image-1",
                        MetadataKeys.ASSET_TYPE, "IMAGE",
                        MetadataKeys.ASSET_CAPTION, imageText,
                        MetadataKeys.TEXT_START, textStart,
                        MetadataKeys.TEXT_END, textEnd
                ))
        ));

        List<DocumentChunk> chunks = service.split(source, text);
        List<DocumentChunk> figureChunks = chunks.stream()
                .filter(chunk -> "FIGURE_CONTEXT".equals(chunk.metadata().get(MetadataKeys.CHUNK_TYPE)))
                .toList();

        assertThat(figureChunks).hasSize(1);
        assertThat(figureChunks.getFirst().content())
                .contains("Caption:\n" + imageText)
                .contains("Before Context:\n" + before)
                .contains("After Context:\n" + after)
                .doesNotContain("【图片：image1.png】");
        assertThat(figureChunks.getFirst().metadata())
                .containsEntry(MetadataKeys.CHUNK_TYPE, "FIGURE_CONTEXT")
                .containsEntry(MetadataKeys.ASSET_IDS, List.of("word-image-1"))
                .containsEntry(MetadataKeys.ASSET_TYPE, "image")
                .containsEntry(MetadataKeys.ASSET_CAPTION, imageText);
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
    void splitShouldRemoveVisualArtifactLineRunsFromPdfImageText() {
        DocumentSplittingServiceImpl service = new DocumentSplittingServiceImpl(new RagProperties(800, 120, 5, 0));
        DocumentSource source = new DocumentSource("source-visual-artifacts", "RAG Survey", "paper.pdf", Map.of("title", "RAG Survey"));
        String text = """
                1 Introduction

                Our contributions are as follows:
                In this survey, we present a thorough and systematic review of RAG methods.
                ar

                X

                iv

                :2

                10 99

                99

                7v

                5

                [

                cs

                .C

                L

                ]

                2
                """;

        List<DocumentChunk> chunks = service.split(source, text);

        assertThat(chunks).hasSize(1);
        assertThat(chunks.getFirst().content())
                .contains("Our contributions are as follows")
                .contains("systematic review of RAG methods")
                .doesNotContain("\nar\n")
                .doesNotContain("10 99")
                .doesNotContain("\ncs\n")
                .doesNotContain(".C");
    }

    @Test
    void splitShouldReturnEmptyListForBlankText() {
        DocumentSplittingServiceImpl service = new DocumentSplittingServiceImpl(new RagProperties(8, 2, 5, 0));
        DocumentSource source = new DocumentSource("source-1", "Paper A", "paper.pdf", Map.of());

        assertThat(service.split(source, "   ")).isEmpty();
    }
}
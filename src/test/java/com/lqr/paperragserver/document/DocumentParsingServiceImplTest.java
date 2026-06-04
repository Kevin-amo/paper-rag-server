package com.lqr.paperragserver.document;

import com.lqr.paperragserver.common.constant.MetadataKeys;
import com.lqr.paperragserver.common.model.ParsedDocument;
import com.lqr.paperragserver.document.service.impl.DocumentParsingServiceImpl;
import com.lqr.paperragserver.document.service.DocumentMultimodalExtractionService;
import com.lqr.paperragserver.document.service.DocumentMultimodalExtractionService.DocumentMultimodalExtractionResult;
import com.lqr.paperragserver.document.service.impl.DocumentMetadataServiceImpl;
import org.apache.tika.Tika;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 文档解析服务的文本、Office、PDF 和多模态降级路径测试。
 */
class DocumentParsingServiceImplTest {

    private final Tika tika = mock(Tika.class);
    private final DocumentMultimodalExtractionService documentMultimodalExtractionService = mock(DocumentMultimodalExtractionService.class);
    private DocumentParsingServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DocumentParsingServiceImpl(tika, documentMultimodalExtractionService, new DocumentMetadataServiceImpl());
    }

    @Test
    void parseShouldUseProvidedMetadataAndTitleForTextDocument() throws Exception {
        byte[] content = "hello rag".getBytes(StandardCharsets.UTF_8);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("sourceId", "paper-1");
        metadata.put("title", "Deep Paper");
        metadata.put("author", "Alice");
        when(tika.detect(content, "paper.txt")).thenReturn("text/plain");
        when(tika.parseToString(any(ByteArrayInputStream.class), any(org.apache.tika.metadata.Metadata.class))).thenReturn("hello rag");

        ParsedDocument parsedDocument = service.parse("paper.txt", content, metadata);

        assertThat(parsedDocument.source().sourceId()).isEqualTo("paper-1");
        assertThat(parsedDocument.source().title()).isEqualTo("Deep Paper");
        assertThat(parsedDocument.source().origin()).isEqualTo("paper.txt");
        assertThat(parsedDocument.source().metadata())
                .containsEntry("fileName", "paper.txt")
                .containsEntry("contentLength", content.length)
                .containsEntry("sourceId", "paper-1")
                .containsEntry("title", "Deep Paper")
                .containsEntry("author", "Alice")
                .containsEntry("contentType", "text/plain")
                .containsEntry("extractionMode", "TEXT")
                .containsEntry("extractedTextLength", 9);
        assertThat(parsedDocument.text()).isEqualTo("hello rag");
        verify(documentMultimodalExtractionService, never()).extract(any(), any());
    }

    @Test
    void parseShouldFallBackToMultimodalExtractionForImageDocuments() throws Exception {
        byte[] content = "image-bytes".getBytes(StandardCharsets.UTF_8);
        when(tika.detect(content, "scan.png")).thenReturn("image/png");
        when(documentMultimodalExtractionService.extract(any(), any()))
                .thenReturn(new DocumentMultimodalExtractionResult("图片中的文字", 1, false));

        ParsedDocument parsedDocument = service.parse("scan.png", content, Map.of("title", "Scan"));

        assertThat(parsedDocument.text()).isEqualTo("图片中的文字");
        assertThat(parsedDocument.source().metadata())
                .containsEntry("contentType", "image/png")
                .containsEntry("extractionMode", "MULTIMODAL")
                .containsEntry("renderedPageCount", 1)
                .containsEntry("extractedTextLength", 6);
        verify(tika, never()).parseToString(any(ByteArrayInputStream.class));
        verify(documentMultimodalExtractionService).extract(any(), any());
    }

    @Test
    void parseShouldFallBackToMultimodalExtractionForScannedPdfWhenTikaExtractsNothing() throws Exception {
        byte[] content = "pdf-bytes".getBytes(StandardCharsets.UTF_8);
        when(tika.detect(content, "scan.pdf")).thenReturn("application/pdf");
        when(tika.parseToString(any(ByteArrayInputStream.class), any(org.apache.tika.metadata.Metadata.class))).thenReturn("   ");
        when(documentMultimodalExtractionService.extract(any(), any()))
                .thenReturn(new DocumentMultimodalExtractionResult("第1页 文本", 1, false));

        ParsedDocument parsedDocument = service.parse("scan.pdf", content, Map.of());

        assertThat(parsedDocument.text()).isEqualTo("第1页 文本");
        assertThat(parsedDocument.source().metadata())
                .containsEntry("contentType", "application/pdf")
                .containsEntry("extractionMode", "MULTIMODAL")
                .containsEntry("renderedPageCount", 1)
                .containsEntry("extractedTextLength", 6);
        verify(documentMultimodalExtractionService).extract(any(), any());
    }

    @Test
    void parseShouldRemoveOfficeImageArtifactLines() throws Exception {
        byte[] content = "docx-bytes".getBytes(StandardCharsets.UTF_8);
        when(tika.detect(content, "paper.docx"))
                .thenReturn("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        when(tika.parseToString(any(ByteArrayInputStream.class), any(org.apache.tika.metadata.Metadata.class))).thenReturn("""
                参考文献
                [1] Spring Boot Reference Documentation.
                image3.png
                image4.PNG
                image5.jpeg
                后续真实文本
                """);

        ParsedDocument parsedDocument = service.parse("paper.docx", content, Map.of());

        assertThat(parsedDocument.text()).contains("参考文献")
                .contains("后续真实文本")
                .doesNotContain("image3.png")
                .doesNotContain("image4.PNG")
                .doesNotContain("image5.jpeg");
        assertThat(parsedDocument.source().metadata())
                .containsEntry("extractionMode", "TEXT")
                .containsEntry("extractedTextLength", parsedDocument.text().length());
    }

    @Test
    void parseShouldMergeEmbeddedWordImagesAtDocumentPosition() throws Exception {
        byte[] content = minimalDocxWithSingleImage();
        when(tika.detect(content, "paper.docx"))
                .thenReturn("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        when(tika.parseToString(any(ByteArrayInputStream.class), any(org.apache.tika.metadata.Metadata.class))).thenReturn("""
                2 系统设计
                image1.png
                2.1 后续章节
                """);
        when(documentMultimodalExtractionService.extract(any(), any()))
                .thenReturn(new DocumentMultimodalExtractionResult("图中展示系统架构", 1, false));

        ParsedDocument parsedDocument = service.parse("paper.docx", content, Map.of());

        assertThat(parsedDocument.text()).containsSubsequence(
                "2 系统设计",
                "【图片：image1.png】\n图中展示系统架构",
                "2.1 后续章节"
        );
        assertThat(parsedDocument.text()).doesNotContain("\nimage1.png\n");
        assertThat(parsedDocument.assets()).hasSize(1);
        assertThat(parsedDocument.assets().getFirst().fileName()).isEqualTo("image1.png");
        assertThat(parsedDocument.assets().getFirst().content()).isEqualTo("fake-image".getBytes(StandardCharsets.UTF_8));
        assertThat(parsedDocument.assets().getFirst().extractedText()).isEqualTo("图中展示系统架构");
        assertThat(parsedDocument.assets().getFirst().textStart()).isNotNull();
        assertThat(parsedDocument.assets().getFirst().textEnd()).isGreaterThan(parsedDocument.assets().getFirst().textStart());
        assertThat(parsedDocument.source().metadata()).containsEntry("assetCount", 1);
        assertThat((java.util.List<?>) parsedDocument.source().metadata().get(MetadataKeys.DOCUMENT_ASSETS))
                .hasSize(1)
                .first()
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry(MetadataKeys.ASSET_TYPE, "IMAGE")
                .containsEntry(MetadataKeys.ASSET_CAPTION, "图中展示系统架构");
        verify(documentMultimodalExtractionService).extract(any(), any());
    }

    @Test
    void parseShouldGenerateStableSourceIdAndFallbackTitle() throws Exception {
        byte[] content = "plain text content".getBytes(StandardCharsets.UTF_8);
        when(tika.detect(content, "paper.txt")).thenReturn("text/plain");
        when(tika.parseToString(any(ByteArrayInputStream.class), any(org.apache.tika.metadata.Metadata.class))).thenReturn("plain text content");

        ParsedDocument first = service.parse("paper.txt", content, Map.of());
        ParsedDocument second = service.parse("paper.txt", content, Map.of());

        assertThat(first.source().sourceId()).isEqualTo(second.source().sourceId());
        assertThat(first.source().title()).isEqualTo("paper.txt");
        assertThat(first.source().origin()).isEqualTo("paper.txt");
        assertThat(first.source().metadata()).containsEntry("fileName", "paper.txt");
        assertThat(String.valueOf(first.source().metadata().get("contentType"))).isEqualTo("text/plain");
    }

    private byte[] minimalDocxWithSingleImage() throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)) {
            writeZipEntry(zipOutputStream, "word/document.xml", """
                    <?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>
                    <w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"
                                xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\"
                                xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">
                      <w:body>
                        <w:p><w:r><w:t>2 系统设计</w:t></w:r></w:p>
                        <w:p><w:r><w:drawing><a:blip r:embed=\"rIdImage1\"/></w:drawing></w:r></w:p>
                        <w:p><w:r><w:t>2.1 后续章节</w:t></w:r></w:p>
                      </w:body>
                    </w:document>
                    """);
            writeZipEntry(zipOutputStream, "word/_rels/document.xml.rels", """
                    <?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>
                    <Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">
                      <Relationship Id=\"rIdImage1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\" Target=\"media/image1.png\"/>
                    </Relationships>
                    """);
            writeZipEntry(zipOutputStream, "word/media/image1.png", "fake-image".getBytes(StandardCharsets.UTF_8));
        }
        return outputStream.toByteArray();
    }

    private void writeZipEntry(ZipOutputStream zipOutputStream, String name, String content) throws Exception {
        writeZipEntry(zipOutputStream, name, content.getBytes(StandardCharsets.UTF_8));
    }

    private void writeZipEntry(ZipOutputStream zipOutputStream, String name, byte[] content) throws Exception {
        zipOutputStream.putNextEntry(new ZipEntry(name));
        zipOutputStream.write(content);
        zipOutputStream.closeEntry();
    }
}
package com.lqr.paperragserver.document.impl;

import com.lqr.paperragserver.common.model.DocumentSource;
import com.lqr.paperragserver.common.constant.MetadataKeys;
import com.lqr.paperragserver.document.service.DocumentMultimodalExtractionService;
import com.lqr.paperragserver.document.service.DocumentMultimodalExtractionService.DocumentMultimodalExtractionResult;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 基于原生多模态模型的文档内容抽取实现。
 */
@Service
@RequiredArgsConstructor
public class DocumentMultimodalExtractionServiceImpl implements DocumentMultimodalExtractionService {

    private static final int PDF_RENDER_DPI = 160;
    private static final int MAX_RENDERED_PAGES = 8;

    private final ChatClient chatClient;

    /**
     * 根据文档类型抽取图片或 PDF 页面中的可见文本。
     *
     * @param source 文档来源信息
     * @param content 原始文件内容
     * @return 多模态抽取结果
     */
    @Override
    public DocumentMultimodalExtractionResult extract(DocumentSource source, byte[] content) {
        if (source == null || content == null || content.length == 0) {
            return new DocumentMultimodalExtractionResult("", 0, false);
        }
        String contentType = stringMetadata(source.metadata(), MetadataKeys.CONTENT_TYPE);
        if (contentType == null || contentType.isBlank()) {
            return new DocumentMultimodalExtractionResult("", 0, false);
        }
        String normalizedContentType = contentType.toLowerCase(Locale.ROOT);
        if (normalizedContentType.startsWith("image/")) {
            String text = extractSingleImage(source, content, normalizedContentType, "整张图片");
            return new DocumentMultimodalExtractionResult(text, 1, false);
        }
        if (normalizedContentType.contains("pdf")) {
            return extractPdfPages(source, content);
        }
        return new DocumentMultimodalExtractionResult("", 0, false);
    }

    /**
     * 将 PDF 前若干页渲染为图片后逐页抽取文本。
     *
     * @param source 文档来源信息
     * @param content PDF 原始内容
     * @return PDF 页面抽取结果
     */
    private DocumentMultimodalExtractionResult extractPdfPages(DocumentSource source, byte[] content) {
        try (PDDocument document = PDDocument.load(content)) {
            int totalPages = document.getNumberOfPages();
            int pageCount = Math.min(totalPages, MAX_RENDERED_PAGES);
            if (pageCount <= 0) {
                return new DocumentMultimodalExtractionResult("", 0, false);
            }
            PDFRenderer renderer = new PDFRenderer(document);
            List<String> pageTexts = new ArrayList<>(pageCount);
            for (int pageIndex = 0; pageIndex < pageCount; pageIndex++) {
                BufferedImage pageImage = renderer.renderImageWithDPI(pageIndex, PDF_RENDER_DPI);
                byte[] pngBytes = toPngBytes(pageImage);
                String pageText = extractSingleImage(
                        source,
                        pngBytes,
                        MimeTypeUtils.IMAGE_PNG.toString(),
                        "第" + (pageIndex + 1) + "页"
                );
                if (!pageText.isBlank()) {
                    pageTexts.add("【第" + (pageIndex + 1) + "页】\n" + pageText.strip());
                }
            }
            return new DocumentMultimodalExtractionResult(
                    String.join("\n\n", pageTexts).strip(),
                    pageCount,
                    totalPages > pageCount
            );
        } catch (IOException ex) {
            throw new IllegalStateException("多模态 PDF 抽取失败", ex);
        }
    }

    /**
     * 调用多模态模型抽取单张图片中的可见文本。
     *
     * @param source 文档来源信息
     * @param content 图片二进制内容
     * @param contentType 图片 MIME 类型
     * @param contextLabel 图片上下文标签
     * @return 模型返回的纯文本内容
     */
    private String extractSingleImage(DocumentSource source,
                                      byte[] content,
                                      String contentType,
                                      String contextLabel) {
        MimeType mimeType = resolveMimeType(contentType);
        Resource resource = new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return safeFilename(source, contextLabel, mimeType);
            }
        };
        String prompt = "请阅读这张文档图片，按原始阅读顺序抽取其中所有可见文字。"
                + "只输出纯文本，不要解释，不要添加 Markdown，不要编造内容。"
                + "如果内容是表格，请尽量按行列顺序还原。";
        String response = chatClient.prompt()
                .user(user -> user.text(prompt).media(mimeType, resource))
                .call()
                .content();
        return normalizeResponse(response);
    }

    /**
     * 解析 MIME 类型，无法解析时回退为 PNG。
     *
     * @param contentType 原始 MIME 类型文本
     * @return 可用于模型请求的 MIME 类型
     */
    private MimeType resolveMimeType(String contentType) {
        try {
            return MimeTypeUtils.parseMimeType(contentType);
        } catch (IllegalArgumentException ex) {
            return MimeTypeUtils.IMAGE_PNG;
        }
    }

    /**
     * 生成适合传递给模型资源对象的安全文件名。
     *
     * @param source 文档来源信息
     * @param contextLabel 图片上下文标签
     * @param mimeType 图片 MIME 类型
     * @return 规范化后的资源文件名
     */
    private String safeFilename(DocumentSource source, String contextLabel, MimeType mimeType) {
        String title = source == null ? null : source.title();
        String normalizedTitle = title == null || title.isBlank() ? "document" : title.replaceAll("[^\\p{IsHan}A-Za-z0-9._-]+", "_");
        return normalizedTitle + "-" + contextLabel + "." + mimeType.getSubtype();
    }

    /**
     * 将渲染得到的页面图片编码为 PNG 字节数组。
     *
     * @param image 页面图片
     * @return PNG 编码后的字节数组
     * @throws IOException 图片编码失败时抛出
     */
    private byte[] toPngBytes(BufferedImage image) throws IOException {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", outputStream);
            return outputStream.toByteArray();
        }
    }

    /**
     * 清理模型响应中的空白和 Markdown 代码围栏。
     *
     * @param response 模型原始响应
     * @return 规范化后的文本
     */
    private String normalizeResponse(String response) {
        if (response == null) {
            return "";
        }
        String normalized = response.strip();
        if (normalized.startsWith("```")) {
            normalized = normalized.replaceFirst("(?s)^```[a-zA-Z0-9_\\-]*\\s*", "");
            normalized = normalized.replaceFirst("\\s*```$", "");
        }
        return normalized.strip();
    }

    /**
     * 从文档元数据中读取字符串值。
     *
     * @param metadata 文档元数据
     * @param key 元数据键名
     * @return 字符串形式的元数据值
     */
    private String stringMetadata(Map<String, Object> metadata, String key) {
        if (metadata == null || !metadata.containsKey(key)) {
            return null;
        }
        Object value = metadata.get(key);
        return value == null ? null : String.valueOf(value);
    }
}
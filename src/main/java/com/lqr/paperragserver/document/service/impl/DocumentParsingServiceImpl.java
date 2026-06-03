package com.lqr.paperragserver.document.service.impl;

import com.lqr.paperragserver.common.model.DocumentAsset;
import com.lqr.paperragserver.common.model.DocumentSource;
import com.lqr.paperragserver.common.model.ParsedDocument;
import com.lqr.paperragserver.common.constant.MetadataKeys;
import com.lqr.paperragserver.document.service.DocumentMultimodalExtractionService;
import com.lqr.paperragserver.document.service.DocumentMultimodalExtractionService.DocumentMultimodalExtractionResult;
import com.lqr.paperragserver.document.service.DocumentParsingService;
import com.lqr.paperragserver.document.service.DocumentMetadataService;
import lombok.RequiredArgsConstructor;
import org.apache.tika.Tika;
import org.apache.tika.metadata.Metadata;
import org.springframework.stereotype.Service;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 基于 Tika 和原生多模态模型的文档解析实现。
 */
@Service
@RequiredArgsConstructor
public class DocumentParsingServiceImpl implements DocumentParsingService {

    private static final Pattern OFFICE_IMAGE_ARTIFACT_LINE = Pattern.compile(
            "^image\\d+\\.(?:png|jpe?g|gif|bmp|webp|tiff?)$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Set<String> SUPPORTED_OFFICE_IMAGE_EXTENSIONS = Set.of(
            "png", "jpg", "jpeg", "gif", "bmp", "webp", "tif", "tiff"
    );
    private static final String WORD_DOCUMENT_XML = "word/document.xml";
    private static final String WORD_DOCUMENT_RELS_XML = "word/_rels/document.xml.rels";

    private final Tika tika;
    private final DocumentMultimodalExtractionService documentMultimodalExtractionService;
    private final DocumentMetadataService documentMetadataService;

    /**
     * 规范化文件信息并生成统一的解析结果。
     *
     * @param fileName 原始文件名
     * @param content 文件内容字节
     * @param metadata 外部传入的元数据
     * @return 组装完成的文档来源信息与正文文本
     */
    @Override
    public ParsedDocument parse(String fileName, byte[] content, Map<String, Object> metadata) {
        Objects.requireNonNull(content, "content 不能为空");
        String normalizedFileName = fileName == null || fileName.isBlank() ? "unknown" : fileName.trim();
        Map<String, Object> mergedMetadata = new LinkedHashMap<>();
        if (metadata != null) {
            mergedMetadata.putAll(metadata);
        }
        mergedMetadata.putIfAbsent(MetadataKeys.FILE_NAME, normalizedFileName);
        mergedMetadata.putIfAbsent(MetadataKeys.CONTENT_LENGTH, content.length);
        String contentType = tika.detect(content, normalizedFileName);
        mergedMetadata.put(MetadataKeys.CONTENT_TYPE, contentType);
        String sourceId = mergedMetadata.containsKey(MetadataKeys.SOURCE_ID)
                ? String.valueOf(mergedMetadata.get(MetadataKeys.SOURCE_ID))
                : UUID.nameUUIDFromBytes(content).toString();
        mergedMetadata.putIfAbsent(MetadataKeys.SOURCE_ID, sourceId);
        String title = mergedMetadata.containsKey(MetadataKeys.TITLE) && !String.valueOf(mergedMetadata.get(MetadataKeys.TITLE)).isBlank()
                ? String.valueOf(mergedMetadata.get(MetadataKeys.TITLE))
                : normalizedFileName;

        DocumentSource provisionalSource = new DocumentSource(sourceId, title, normalizedFileName, mergedMetadata);
        DocumentSource enrichedSource = documentMetadataService.enrich(provisionalSource, metadata);
        mergedMetadata = new LinkedHashMap<>(enrichedSource.metadata());
        ExtractionResult extractionResult = extractContent(enrichedSource, content, contentType, mergedMetadata);
        enrichedSource = documentMetadataService.enrich(new DocumentSource(sourceId, enrichedSource.title(), normalizedFileName, mergedMetadata), Map.of());
        mergedMetadata = new LinkedHashMap<>(enrichedSource.metadata());
        mergedMetadata.put(MetadataKeys.EXTRACTION_MODE, extractionResult.mode().name());
        mergedMetadata.put(MetadataKeys.EXTRACTED_TEXT_LENGTH, extractionResult.text().length());
        if (!extractionResult.assets().isEmpty()) {
            mergedMetadata.put(MetadataKeys.ASSET_COUNT, extractionResult.assets().size());
            mergedMetadata.put(MetadataKeys.DOCUMENT_ASSETS, extractionResult.assets().stream()
                    .map(this::assetMetadata)
                    .toList());
        }
        if (extractionResult.renderedPageCount() > 0) {
            mergedMetadata.put(MetadataKeys.RENDERED_PAGE_COUNT, extractionResult.renderedPageCount());
        }
        if (extractionResult.truncated()) {
            mergedMetadata.put(MetadataKeys.MULTIMODAL_TRUNCATED, true);
        }
        DocumentSource source = new DocumentSource(sourceId, enrichedSource.title(), normalizedFileName, mergedMetadata);
        return new ParsedDocument(source, extractionResult.text(), extractionResult.assets());
    }

    /**
     * 根据 Tika 解析出的文件属性补充常见论文元数据候选键。
     */
    private void mergeTikaMetadata(Map<String, Object> metadata, Metadata tikaMetadata) {
        putTikaValueIfAbsent(metadata, MetadataKeys.TITLE, tikaMetadata, "dc:title", "title");
        putTikaValueIfAbsent(metadata, MetadataKeys.AUTHORS, tikaMetadata, "dc:creator", "Author", "creator", "meta:author");
        putTikaValueIfAbsent(metadata, MetadataKeys.ABSTRACT_TEXT, tikaMetadata, "dc:description", "description", "subject");
        putTikaValueIfAbsent(metadata, MetadataKeys.KEYWORDS, tikaMetadata, "meta:keyword", "Keywords", "pdf:docinfo:keywords");
        putTikaValueIfAbsent(metadata, MetadataKeys.PUBLISH_YEAR, tikaMetadata, "dcterms:created", "created", "Creation-Date", "date");
    }

    /**
     * 当目标键不存在或值为空时，从 Tika 元数据中按候选键列表查找第一个非空值写入。
     *
     * @param metadata 目标元数据映射
     * @param targetKey 目标键名
     * @param tikaMetadata Tika 解析出的元数据
     * @param tikaKeys Tika 元数据中的候选键列表
     */
    private void putTikaValueIfAbsent(Map<String, Object> metadata, String targetKey, Metadata tikaMetadata, String... tikaKeys) {
        if (metadata.containsKey(targetKey) && metadata.get(targetKey) != null && !String.valueOf(metadata.get(targetKey)).isBlank()) {
            return;
        }
        for (String tikaKey : tikaKeys) {
            String value = tikaMetadata.get(tikaKey);
            if (value != null && !value.isBlank()) {
                metadata.put(targetKey, value);
                return;
            }
        }
    }

    /**
     * 根据文件类型选择文本抽取路径，必要时回退到多模态抽取。
     */
    private ExtractionResult extractContent(DocumentSource source, byte[] content, String contentType, Map<String, Object> metadata) {
        String normalizedContentType = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        if (normalizedContentType.startsWith("image/")) {
            DocumentMultimodalExtractionResult result = documentMultimodalExtractionService.extract(source, content);
            String text = normalizeExtractedText(result.text());
            if (text.isBlank()) {
                throw new IllegalStateException("图片文档未提取到可用文本");
            }
            return new ExtractionResult(text, ExtractionMode.MULTIMODAL, result.pageCount(), result.truncated(), List.of());
        }

        TextExtractionResult tikaText = extractTextWithTika(source, content, contentType, metadata);
        if (normalizedContentType.contains("pdf") && tikaText.text().isBlank()) {
            DocumentMultimodalExtractionResult result = documentMultimodalExtractionService.extract(source, content);
            String text = normalizeExtractedText(result.text());
            if (text.isBlank()) {
                throw new IllegalStateException("扫描版 PDF 未提取到可用文本");
            }
            return new ExtractionResult(text, ExtractionMode.MULTIMODAL, result.pageCount(), result.truncated(), List.of());
        }

        return new ExtractionResult(tikaText.text(), ExtractionMode.TEXT, 0, false, tikaText.assets());
    }

    /**
     * 使用 Tika 抽取文本，并对 Office 文档中的图片内容做增强处理。
     */
    private TextExtractionResult extractTextWithTika(DocumentSource source, byte[] content, String contentType, Map<String, Object> metadata) {
        try {
            Metadata tikaMetadata = new Metadata();
            tikaMetadata.set("resourceName", source.origin());
            String extractedText = normalizeExtractedText(tika.parseToString(new ByteArrayInputStream(content), tikaMetadata));
            mergeTikaMetadata(metadata, tikaMetadata);
            if (!isOfficeDocument(contentType)) {
                return new TextExtractionResult(extractedText, List.of());
            }
            String cleanedText = removeOfficeImageArtifactLines(extractedText);
            if (!isWordprocessingDocument(contentType)) {
                return new TextExtractionResult(cleanedText, List.of());
            }
            return mergeWordImagesIntoText(source, content, cleanedText);
        } catch (Exception ex) {
            throw new IllegalStateException("文档解析失败", ex);
        }
    }

    /**
     * 判断 MIME 类型是否属于 Office 文档。
     */
    private boolean isOfficeDocument(String contentType) {
        if (contentType == null) {
            return false;
        }
        String normalized = contentType.toLowerCase(Locale.ROOT);
        return normalized.contains("officedocument")
                || normalized.contains("msword")
                || normalized.contains("vnd.ms-");
    }

    /**
     * 判断 MIME 类型是否属于 Word 文档。
     */
    private boolean isWordprocessingDocument(String contentType) {
        if (contentType == null) {
            return false;
        }
        String normalized = contentType.toLowerCase(Locale.ROOT);
        return normalized.contains("wordprocessingml.document") || normalized.contains("msword");
    }

    /**
     * 将 Word 正文和嵌入图片抽取结果按原始顺序合并。
     */
    private TextExtractionResult mergeWordImagesIntoText(DocumentSource source, byte[] content, String fallbackText) {
        try {
            WordPackage wordPackage = readWordPackage(content);
            if (wordPackage.documentXml() == null || wordPackage.relationshipXml() == null) {
                return new TextExtractionResult(fallbackText, List.of());
            }
            Map<String, String> relationshipTargets = readImageRelationshipTargets(wordPackage.relationshipXml());
            if (relationshipTargets.isEmpty()) {
                return new TextExtractionResult(fallbackText, List.of());
            }
            TextExtractionResult orderedText = readWordDocumentTextWithImages(source, wordPackage.documentXml(), relationshipTargets, wordPackage.entries());
            return orderedText.text().isBlank() ? new TextExtractionResult(fallbackText, List.of()) : orderedText;
        } catch (RuntimeException ex) {
            return new TextExtractionResult(fallbackText, List.of());
        }
    }

    /**
     * 读取 Word 压缩包中的正文 XML、关系 XML 和全部条目。
     */
    private WordPackage readWordPackage(byte[] content) {
        Map<String, byte[]> entries = new HashMap<>();
        String documentXml = null;
        String relationshipXml = null;
        try (ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(content))) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                String name = entry.getName();
                byte[] bytes = zipInputStream.readAllBytes();
                entries.put(name, bytes);
                if (WORD_DOCUMENT_XML.equals(name)) {
                    documentXml = new String(bytes, StandardCharsets.UTF_8);
                } else if (WORD_DOCUMENT_RELS_XML.equals(name)) {
                    relationshipXml = new String(bytes, StandardCharsets.UTF_8);
                }
            }
        } catch (Exception ex) {
            throw new IllegalStateException("读取 Word 文档结构失败", ex);
        }
        return new WordPackage(documentXml, relationshipXml, entries);
    }

    /**
     * 从 Word 关系 XML 中读取图片关系 ID 与文件路径的映射。
     */
    private Map<String, String> readImageRelationshipTargets(String relationshipXml) {
        Map<String, String> targets = new HashMap<>();
        Element root = parseXml(relationshipXml).getDocumentElement();
        NodeList relationships = root.getElementsByTagNameNS("*", "Relationship");
        for (int i = 0; i < relationships.getLength(); i++) {
            Element relationship = (Element) relationships.item(i);
            String type = relationship.getAttribute("Type");
            String id = relationship.getAttribute("Id");
            String target = relationship.getAttribute("Target");
            if (type != null && type.endsWith("/image") && !id.isBlank() && !target.isBlank()) {
                targets.put(id, normalizeWordTarget(target));
            }
        }
        return targets;
    }

    /**
     * 按 Word 文档正文顺序合并段落文本和嵌入图片抽取文本。
     */
    private TextExtractionResult readWordDocumentTextWithImages(DocumentSource source,
                                                               String documentXml,
                                                               Map<String, String> relationshipTargets,
                                                               Map<String, byte[]> entries) {
        Element body = firstElementByLocalName(parseXml(documentXml).getDocumentElement(), "body");
        if (body == null) {
            return new TextExtractionResult("", List.of());
        }
        StringBuilder textBuilder = new StringBuilder();
        List<DocumentAsset> assets = new ArrayList<>();
        NodeList children = body.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node instanceof Element element && "p".equals(element.getLocalName())) {
                String paragraphText = paragraphText(element);
                if (!paragraphText.isBlank() && !OFFICE_IMAGE_ARTIFACT_LINE.matcher(paragraphText.strip()).matches()) {
                    appendBlock(textBuilder, paragraphText.strip());
                }
                for (String relationshipId : imageRelationshipIds(element)) {
                    String imagePath = relationshipTargets.get(relationshipId);
                    byte[] imageBytes = imagePath == null ? null : entries.get(imagePath);
                    if (imageBytes == null || !isSupportedOfficeImage(imagePath)) {
                        continue;
                    }
                    String imageText = extractEmbeddedImageText(source, imagePath, imageBytes);
                    if (!imageText.isBlank()) {
                        String fileName = imagePath.substring(imagePath.lastIndexOf('/') + 1);
                        String imageBlock = "【图片：" + fileName + "】\n" + imageText;
                        int textStart = appendBlock(textBuilder, imageBlock);
                        int textEnd = textStart + imageBlock.length();
                        assets.add(toDocumentAsset(source, assets.size(), imagePath, fileName, imageBytes, imageText, textStart, textEnd));
                    }
                }
            }
        }
        return new TextExtractionResult(normalizeExtractedText(textBuilder.toString()), assets);
    }

    /**
     * 安全解析 Word XML 文本。
     */
    private org.w3c.dom.Document parseXml(String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            return factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("解析 Word XML 失败", ex);
        }
    }

    /**
     * 查找指定本地名称的第一个 XML 元素。
     */
    private Element firstElementByLocalName(Element root, String localName) {
        NodeList nodes = root.getElementsByTagNameNS("*", localName);
        return nodes.getLength() == 0 ? null : (Element) nodes.item(0);
    }

    /**
     * 提取 Word 段落中的纯文本节点内容。
     */
    private String paragraphText(Element paragraph) {
        StringBuilder builder = new StringBuilder();
        NodeList textNodes = paragraph.getElementsByTagNameNS("*", "t");
        for (int i = 0; i < textNodes.getLength(); i++) {
            builder.append(textNodes.item(i).getTextContent());
        }
        return builder.toString();
    }

    /**
     * 提取段落中引用的图片关系 ID 列表。
     */
    private List<String> imageRelationshipIds(Element paragraph) {
        List<String> ids = new ArrayList<>();
        NodeList blips = paragraph.getElementsByTagNameNS("*", "blip");
        for (int i = 0; i < blips.getLength(); i++) {
            Element blip = (Element) blips.item(i);
            String embed = blip.getAttributeNS("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "embed");
            if (!embed.isBlank()) {
                ids.add(embed);
            }
        }
        return ids;
    }

    /**
     * 将 Word 嵌入图片及其抽取文本封装为文档资产。
     */
    private DocumentAsset toDocumentAsset(DocumentSource source,
                                          int assetIndex,
                                          String imagePath,
                                          String fileName,
                                          byte[] imageBytes,
                                          String extractedText,
                                          int textStart,
                                          int textEnd) {
        String contentType = imageContentType(imagePath);
        String contentHash = sha256(imageBytes);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put(MetadataKeys.EMBEDDED_IMAGE_PATH, imagePath);
        return new DocumentAsset(
                UUID.nameUUIDFromBytes((source.sourceId() + "::" + imagePath + "::" + contentHash).getBytes(StandardCharsets.UTF_8)).toString(),
                source.sourceId(),
                assetIndex,
                "IMAGE",
                fileName,
                contentType,
                imageBytes.length,
                contentHash,
                imageBytes,
                extractedText,
                textStart,
                textEnd,
                metadata
        );
    }

    /**
     * 向文本构建器追加独立内容块并返回起始偏移。
     */
    private int appendBlock(StringBuilder builder, String block) {
        if (!builder.isEmpty()) {
            builder.append("\n\n");
        }
        int start = builder.length();
        builder.append(block);
        return start;
    }

    /**
     * 抽取 Word 嵌入图片中的可见文本。
     */
    private String extractEmbeddedImageText(DocumentSource source, String imagePath, byte[] imageBytes) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (source.metadata() != null) {
            metadata.putAll(source.metadata());
        }
        String contentType = imageContentType(imagePath);
        metadata.put(MetadataKeys.CONTENT_TYPE, contentType);
        metadata.put(MetadataKeys.EMBEDDED_IMAGE_PATH, imagePath);
        DocumentSource imageSource = new DocumentSource(source.sourceId(), source.title(), source.origin(), metadata);
        DocumentMultimodalExtractionResult result = documentMultimodalExtractionService.extract(imageSource, imageBytes);
        return normalizeExtractedText(result.text());
    }

    /**
     * 根据图片路径推断图片 MIME 类型。
     */
    private String imageContentType(String imagePath) {
        String extension = extension(imagePath);
        return switch (extension) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "gif" -> "image/gif";
            case "bmp" -> "image/bmp";
            case "webp" -> "image/webp";
            case "tif", "tiff" -> "image/tiff";
            default -> "image/png";
        };
    }

    /**
     * 判断 Office 嵌入图片格式是否支持处理。
     */
    private boolean isSupportedOfficeImage(String imagePath) {
        return SUPPORTED_OFFICE_IMAGE_EXTENSIONS.contains(extension(imagePath));
    }

    /**
     * 提取路径中的小写文件扩展名。
     */
    private String extension(String path) {
        if (path == null) {
            return "";
        }
        int dot = path.lastIndexOf('.');
        return dot < 0 ? "" : path.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /**
     * 将 Word 关系目标路径规范化为压缩包内路径。
     */
    private String normalizeWordTarget(String target) {
        String normalized = target.replace('\\', '/');
        if (normalized.startsWith("/")) {
            return normalized.substring(1);
        }
        return normalized.startsWith("word/") ? normalized : "word/" + normalized;
    }

    /**
     * 移除 Tika 抽取结果中由 Office 图片文件名产生的噪声行。
     */
    private String removeOfficeImageArtifactLines(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        StringBuilder builder = new StringBuilder(text.length());
        for (String line : text.split("\\R", -1)) {
            if (OFFICE_IMAGE_ARTIFACT_LINE.matcher(line.strip()).matches()) {
                continue;
            }
            builder.append(line).append('\n');
        }
        return normalizeExtractedText(builder.toString());
    }

    /**
     * 将文档资产信息转换为可写入文档元数据的摘要结构。
     */
    private Map<String, Object> assetMetadata(DocumentAsset asset) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put(MetadataKeys.ASSET_ID, asset.assetId());
        metadata.put(MetadataKeys.ASSET_TYPE, asset.assetType());
        metadata.put(MetadataKeys.FILE_NAME, asset.fileName());
        metadata.put(MetadataKeys.CONTENT_TYPE, asset.contentType());
        metadata.put(MetadataKeys.FILE_SIZE, asset.fileSize());
        metadata.put(MetadataKeys.CONTENT_HASH, asset.contentHash());
        metadata.put(MetadataKeys.TEXT_START, asset.textStart());
        metadata.put(MetadataKeys.TEXT_END, asset.textEnd());
        if (asset.metadata() != null) {
            metadata.putAll(asset.metadata());
        }
        return metadata;
    }

    /**
     * 规范化抽取文本，空值回退为空字符串。
     */
    private String normalizeExtractedText(String text) {
        return text == null ? "" : text.strip();
    }

    /**
     * 计算二进制内容的 SHA-256 十六进制摘要。
     */
    private String sha256(byte[] content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(content == null ? new byte[0] : content);
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", ex);
        }
    }

    private enum ExtractionMode {
        TEXT,
        MULTIMODAL
    }

    private record WordPackage(String documentXml, String relationshipXml, Map<String, byte[]> entries) {
    }

    private record TextExtractionResult(String text, List<DocumentAsset> assets) {
    }

    private record ExtractionResult(String text, ExtractionMode mode, int renderedPageCount, boolean truncated, List<DocumentAsset> assets) {
    }
}
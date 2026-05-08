package com.lqr.paperragserver.document;

import com.lqr.paperragserver.common.DocumentSource;
import com.lqr.paperragserver.common.ParsedDocument;
import lombok.RequiredArgsConstructor;
import org.apache.tika.Tika;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 基于 Tika 的文档解析实现。
 *
 * <p>这里优先提取正文文本，再结合文件名和内容类型生成统一的文档来源信息。</p>
 */
@Service
public class TikaDocumentParsingService implements DocumentParsingService {

    private final Tika tika;

    public TikaDocumentParsingService(Tika tika) {
        this.tika = tika;
    }

    @Override
    public DocumentSource parse(String fileName, byte[] content, Map<String, Object> metadata) {
        Objects.requireNonNull(content, "content 不能为空");
        String normalizedFileName = fileName == null || fileName.isBlank() ? "unknown" : fileName.trim();
        Map<String, Object> mergedMetadata = new LinkedHashMap<>();
        if (metadata != null) {
            mergedMetadata.putAll(metadata);
        }
        mergedMetadata.putIfAbsent("fileName", normalizedFileName);
        mergedMetadata.putIfAbsent("contentLength", content.length);
        String contentType = tika.detect(content, normalizedFileName);
        mergedMetadata.putIfAbsent("contentType", contentType);
        String sourceId = mergedMetadata.containsKey("sourceId")
                ? String.valueOf(mergedMetadata.get("sourceId"))
                : UUID.nameUUIDFromBytes((normalizedFileName + "::" + content.length).getBytes(StandardCharsets.UTF_8)).toString();
        mergedMetadata.putIfAbsent("sourceId", sourceId);
        String title = mergedMetadata.containsKey("title") && !String.valueOf(mergedMetadata.get("title")).isBlank()
                ? String.valueOf(mergedMetadata.get("title"))
                : normalizedFileName;
        return new DocumentSource(sourceId, title, normalizedFileName, mergedMetadata);
    }

    /**
     * 提取纯文本内容。
     *
     * @param content 原始文件字节
     * @return 提取出的正文文本
     */
    public String extractText(byte[] content) {
        try {
            return tika.parseToString(new ByteArrayInputStream(content));
        } catch (Exception ex) {
            throw new IllegalStateException("文档解析失败", ex);
        }
    }
}
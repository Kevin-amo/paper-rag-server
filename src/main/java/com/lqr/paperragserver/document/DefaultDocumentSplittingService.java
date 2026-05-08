package com.lqr.paperragserver.document;

import com.lqr.paperragserver.common.DocumentChunk;
import com.lqr.paperragserver.common.DocumentSource;
import com.lqr.paperragserver.config.RagProperties;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 默认的文本切分实现。
 *
 * <p>按固定窗口切分正文，并保留基础位置信息，方便后续引用回溯。</p>
 */
@Service
public class DefaultDocumentSplittingService implements DocumentSplittingService {

    private final RagProperties ragProperties;

    public DefaultDocumentSplittingService(RagProperties ragProperties) {
        this.ragProperties = ragProperties;
    }

    @Override
    public List<DocumentChunk> split(DocumentSource source, String fullText) {
        Objects.requireNonNull(source, "source 不能为空");
        if (fullText == null || fullText.isBlank()) {
            return List.of();
        }
        List<DocumentChunk> chunks = new ArrayList<>();
        int chunkSize = ragProperties.chunkSize();
        int overlap = Math.min(ragProperties.chunkOverlap(), Math.max(0, chunkSize - 1));
        int index = 0;
        int start = 0;
        while (start < fullText.length()) {
            int end = Math.min(fullText.length(), start + chunkSize);
            String content = fullText.substring(start, end).trim();
            if (!content.isBlank()) {
                Map<String, Object> metadata = new LinkedHashMap<>();
                if (source.metadata() != null) {
                    metadata.putAll(source.metadata());
                }
                metadata.put("chunkStart", start);
                metadata.put("chunkEnd", end);
                metadata.put("chunkLength", content.length());
                chunks.add(new DocumentChunk(
                        UUID.nameUUIDFromBytes((source.sourceId() + "::" + index + "::" + start + "::" + end).getBytes()).toString(),
                        source.sourceId(),
                        index,
                        content,
                        metadata
                ));
                index++;
            }
            if (end >= fullText.length()) {
                break;
            }
            start = Math.max(end - overlap, start + 1);
        }
        return chunks;
    }
}
package com.lqr.papermind.document.structured.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lqr.papermind.document.entity.DocumentEntity;
import com.lqr.papermind.document.mapper.DocumentMapper;
import com.lqr.papermind.document.service.DocumentPersistenceService;
import com.lqr.papermind.document.structured.entity.PaperStructuredParseEntity;
import com.lqr.papermind.document.structured.mapper.PaperStructuredParseMapper;
import com.lqr.papermind.document.structured.model.ModelCompletionResult;
import com.lqr.papermind.document.structured.model.StructuredParseResult;
import com.lqr.papermind.document.structured.service.PaperSectionRuleParser;
import com.lqr.papermind.document.structured.service.PaperStructuredMergePolicy;
import com.lqr.papermind.document.structured.service.PaperStructuredModelCompleter;
import com.lqr.papermind.document.structured.service.PaperStructuredParseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 论文结构化解析编排服务默认实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaperStructuredParseServiceImpl implements PaperStructuredParseService {

    private static final int ERROR_MESSAGE_MAX_LENGTH = 4000;

    private final PaperStructuredParseMapper structuredParseMapper;
    private final DocumentMapper documentMapper;
    private final DocumentPersistenceService documentPersistenceService;
    private final PaperSectionRuleParser ruleParser;
    private final PaperStructuredModelCompleter modelCompleter;
    private final PaperStructuredMergePolicy mergePolicy;
    private final ObjectMapper objectMapper;

    @Override
    public Optional<PaperStructuredParseEntity> find(UUID ownerUserId, String sourceId) {
        return Optional.ofNullable(structuredParseMapper.selectOne(new LambdaQueryWrapper<PaperStructuredParseEntity>()
                .eq(PaperStructuredParseEntity::getOwnerUserId, ownerUserId)
                .eq(PaperStructuredParseEntity::getSourceId, sourceId)));
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public PaperStructuredParseEntity generate(UUID ownerUserId, String sourceId) {
        return run(ownerUserId, sourceId);
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public PaperStructuredParseEntity regenerate(UUID ownerUserId, String sourceId) {
        return run(ownerUserId, sourceId);
    }

    private PaperStructuredParseEntity run(UUID ownerUserId, String sourceId) {
        DocumentPersistenceService.DocumentDetail document = documentPersistenceService.findAnyDocument(ownerUserId, sourceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "文档不存在"));
        DocumentEntity entity = requireDocumentEntity(ownerUserId, sourceId);
        String rawText = document.contentText() == null ? "" : document.contentText();
        try {
            StructuredParseResult ruleResult = ruleParser.parse(document);
            ModelCompletionResult modelCompletion = modelCompleter.complete(document, ruleResult);
            StructuredParseResult mergedResult = mergePolicy.merge(ruleResult, modelCompletion.result());
            String status = modelCompletion.errorMessage() == null ? "COMPLETED" : "COMPLETED";
            structuredParseMapper.upsertResult(
                    UUID.randomUUID(),
                    ownerUserId,
                    entity.getId(),
                    sourceId,
                    rawText,
                    json(ruleResult.content()),
                    json(modelCompletion.result().content()),
                    json(mergedResult.content()),
                    json(evidencePayload(mergedResult)),
                    json(mergedResult.missingFields()),
                    json(mergedResult.lowConfidenceFields()),
                    modelCompletion.rawModelOutput(),
                    status,
                    cut(modelCompletion.errorMessage())
            );
            return find(ownerUserId, sourceId).orElseThrow(() -> new IllegalStateException("结构化解析结果保存失败"));
        } catch (RuntimeException ex) {
            structuredParseMapper.upsertFailed(UUID.randomUUID(), ownerUserId, entity.getId(), sourceId, rawText, cut(ex.getMessage()));
            log.warn("paper.structured.parse.failed ownerUserId={} sourceId={}", ownerUserId, sourceId, ex);
            return find(ownerUserId, sourceId).orElseThrow(() -> ex);
        }
    }

    private DocumentEntity requireDocumentEntity(UUID ownerUserId, String sourceId) {
        DocumentEntity entity = documentMapper.selectOne(new LambdaQueryWrapper<DocumentEntity>()
                .eq(DocumentEntity::getOwnerUserId, ownerUserId)
                .eq(DocumentEntity::getSourceId, sourceId));
        if (entity == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "文档不存在");
        }
        return entity;
    }

    private Map<String, Object> evidencePayload(StructuredParseResult result) {
        Map<String, Object> payload = new LinkedHashMap<>();
        result.evidence().forEach((field, evidence) -> payload.put(field, Map.of(
                "source", evidence.source(),
                "confidence", evidence.confidence(),
                "missing", evidence.missing(),
                "evidence", evidence.evidence() == null ? "" : evidence.evidence()
        )));
        return payload;
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("结构化解析结果 JSON 序列化失败", ex);
        }
    }

    private String cut(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.length() <= ERROR_MESSAGE_MAX_LENGTH ? value : value.substring(0, ERROR_MESSAGE_MAX_LENGTH);
    }
}